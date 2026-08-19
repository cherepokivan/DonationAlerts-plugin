package ru.cherepokivan.donationalerts.donationalerts;

import java.math.BigDecimal;

public record Donation(String id, String username, BigDecimal amount, String currency) { }
