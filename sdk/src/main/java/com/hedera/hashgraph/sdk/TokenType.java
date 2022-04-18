/*-
 *
 * Hedera Java SDK
 *
 * Copyright (C) 2020 - 2022 Hedera Hashgraph, LLC
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 */
package com.hedera.hashgraph.sdk;

public enum TokenType {
    /**
     * Interchangeable value with one another, where any quantity of them has the same value as another equal quantity if they are in the same class.
     * Share a single set of properties, not distinct from one another. Simply represented as a balance or quantity to a given Hedera account.
     */
    FUNGIBLE_COMMON(com.hedera.hashgraph.sdk.proto.TokenType.FUNGIBLE_COMMON),
    /**
     * Unique, not interchangeable with other tokens of the same type as they typically have different values.
     * Individually traced and can carry unique properties (e.g. serial number).
     */
    NON_FUNGIBLE_UNIQUE(com.hedera.hashgraph.sdk.proto.TokenType.NON_FUNGIBLE_UNIQUE);

    final com.hedera.hashgraph.sdk.proto.TokenType code;

    TokenType(com.hedera.hashgraph.sdk.proto.TokenType code) {
        this.code = code;
    }

    static TokenType valueOf(com.hedera.hashgraph.sdk.proto.TokenType code) {
        switch (code) {
            case FUNGIBLE_COMMON:
                return FUNGIBLE_COMMON;
            case NON_FUNGIBLE_UNIQUE:
                return NON_FUNGIBLE_UNIQUE;
            default:
                throw new IllegalStateException("(BUG) unhandled TokenType");
        }
    }

    @Override
    public String toString() {
        switch (this) {
            case FUNGIBLE_COMMON:
                return "FUNGIBLE_COMMON";
            case NON_FUNGIBLE_UNIQUE:
                return "NON_FUNGIBLE_UNIQUE";
            default:
                return "<UNRECOGNIZED VALUE>";
        }
    }
}
