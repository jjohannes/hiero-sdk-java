package com.hedera.hashgraph.sdk;

import com.google.protobuf.InvalidProtocolBufferException;
import com.hedera.hashgraph.sdk.proto.Query;
import com.hedera.hashgraph.sdk.proto.QueryHeader;
import com.hedera.hashgraph.sdk.proto.Response;
import com.hedera.hashgraph.sdk.proto.ResponseHeader;
import com.hedera.hashgraph.sdk.proto.ResponseType;
import com.hedera.hashgraph.sdk.proto.Transaction;
import com.hedera.hashgraph.sdk.proto.TransactionBody;
import io.grpc.MethodDescriptor;
import java8.util.concurrent.CompletableFuture;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

public abstract class QueryBuilder<O, T extends QueryBuilder<O, T>> extends HederaExecutable<Query, Response, O> {
    private final Query.Builder builder;

    private final QueryHeader.Builder headerBuilder;

    @Nullable
    private TransactionId paymentTransactionId;

    @Nullable
    private List<Transaction> paymentTransactions;

    @Nullable
    private List<AccountId> paymentTransactionNodeIds;

    private int nextPaymentTransactionIndex = 0;

    @Nullable
    private Long queryPayment;

    QueryBuilder() {
        builder = Query.newBuilder();
        headerBuilder = QueryHeader.newBuilder();
    }

    public T setQueryPayment(long queryPayment) {
        this.queryPayment = queryPayment;

        // noinspection unchecked
        return (T) this;
    }

    protected boolean isPaymentRequired() {
        // nearly all queries require a payment
        return true;
    }

    /**
     * Called in {@link #makeRequest} just before the query is built. The intent is for the derived
     * class to assign their data variant to the query.
     */
    protected abstract void onMakeRequest(Query.Builder queryBuilder, QueryHeader header);

    /**
     * The derived class should access its response header and return.
     */
    protected abstract ResponseHeader mapResponseHeader(Response response);

    protected abstract QueryHeader mapRequestHeader(Query request);

    @Override
    protected CompletableFuture<Void> onExecuteAsync(Client client) {
        if ((paymentTransactions != null) || !isPaymentRequired()) {
            return CompletableFuture.completedFuture(null);
        }

        // Generate payment transactions if one was
        // not set and payment is required

        var operator = client.getOperator();

        if (operator == null) {
            throw new IllegalStateException(
                "`client` must have an `operator` or an explicit payment transaction must be provided");
        }

        return CompletableFuture.supplyAsync(() -> {
            if (queryPayment == null) {
                // No payment was specified so we need to go ask
                // This is a query in its own right so we use a nested future here

                return new QueryCostQuery().executeAsync(client);
            }

            return CompletableFuture.completedFuture(queryPayment);
        }, client.executor)
            .thenCompose(x -> x)
            .thenAccept((paymentAmount) -> {
                paymentTransactionId = TransactionId.generate(operator.accountId);

                // Like how TransactionBuilder has to build (N / 3) native transactions to handle multi-node retry,
                // so too does the QueryBuilder for payment transactions

                var size = client.getNumberOfNodesForSuperMajority();
                paymentTransactions = new ArrayList<>(size);
                paymentTransactionNodeIds = new ArrayList<>(size);

                for (var i = 0; i < size; ++i) {
                    var nodeId = client.getNextNodeId();

                    paymentTransactionNodeIds.add(nodeId);
                    paymentTransactions.add(new CryptoTransferTransaction()
                        .setTransactionId(paymentTransactionId)
                        .setNodeAccountId(nodeId)
                        .setMaxTransactionFee(100000000) // 1 Hbar
                        .addSender(operator.accountId, paymentAmount)
                        .addRecipient(nodeId, paymentAmount)
                        .build(null)
                        .signWith(operator.publicKey, operator.transactionSigner)
                        .makeRequest(client)
                    );
                }
            });
    }

    @Override
    protected final Query makeRequest(Client client) {
        // If payment is required, set the next payment transaction on the query
        if (isPaymentRequired() && paymentTransactions != null) {
            headerBuilder.setPayment(paymentTransactions.get(nextPaymentTransactionIndex));

            // each time we move our cursor to the next transaction
            // wrapping around to ensure we are cycling
            nextPaymentTransactionIndex = (nextPaymentTransactionIndex + 1) % paymentTransactions.size();
        }

        // Delegate to the derived class to apply the header because the common header struct is
        // within the nested type
        onMakeRequest(builder, headerBuilder.setResponseType(ResponseType.ANSWER_ONLY).build());

        return builder.build();
    }

    @Override
    protected final Status mapResponseStatus(Response response) {
        var preCheckCode = mapResponseHeader(response).getNodeTransactionPrecheckCode();

        return Status.valueOf(preCheckCode);
    }

    @Override
    protected final AccountId getNodeId(Client client) {
        if (paymentTransactionNodeIds != null) {
            // If this query needs a payment transaction we need to pick the node ID from the next
            // payment transaction
            return paymentTransactionNodeIds.get(nextPaymentTransactionIndex);
        }

        // Otherwise just pick the next node in the round robin
        return client.getNextNodeId();
    }

    @Override
    protected TransactionId getTransactionId() {
        // this is only called on an error about either the payment transaction or missing a payment transaction
        // as we make sure the latter can't happen, this will never be null
        return Objects.requireNonNull(paymentTransactionId);
    }

    @Override
    @SuppressWarnings("LiteProtoToString")
    protected String debugToString(Query request) {
        StringBuilder builder = new StringBuilder(request.toString());

        var queryHeader = mapRequestHeader(request);
        if (queryHeader.hasPayment()) {
            builder.append("\n");

            try {
                builder.append(TransactionBody.parseFrom(queryHeader.getPayment().getBodyBytes()).toString());
            } catch (InvalidProtocolBufferException e) {
                throw new RuntimeException(e);
            }
        }

        return builder.toString();
    }

    @SuppressWarnings("NullableDereference")
    private class QueryCostQuery extends QueryBuilder<Long, QueryCostQuery> {
        @Override
        protected void onMakeRequest(Query.Builder queryBuilder, QueryHeader header) {
            QueryBuilder.this.onMakeRequest(queryBuilder,
                headerBuilder.setResponseType(ResponseType.COST_ANSWER).build());
        }

        @Override
        protected ResponseHeader mapResponseHeader(Response response) {
            return QueryBuilder.this.mapResponseHeader(response);
        }

        @Override
        protected QueryHeader mapRequestHeader(Query request) {
            return QueryBuilder.this.mapRequestHeader(request);
        }

        @Override
        protected Long mapResponse(Response response) {
            return mapResponseHeader(response).getCost();
        }

        @Override
        protected MethodDescriptor<Query, Response> getMethodDescriptor() {
            return QueryBuilder.this.getMethodDescriptor();
        }

        @Override
        protected boolean isPaymentRequired() {
            // combo breaker
            return false;
        }
    }
}
