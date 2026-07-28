package com.won.autoinvestor.broker.domain;

import java.time.OffsetDateTime;

public record AccessToken(String token, OffsetDateTime expiresAt) {
}
