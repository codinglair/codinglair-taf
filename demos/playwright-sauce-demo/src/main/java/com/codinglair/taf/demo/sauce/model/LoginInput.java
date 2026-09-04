package com.codinglair.taf.demo.sauce.model;

import com.codinglair.taf.runtime.definition.SecretField;
import com.codinglair.taf.runtime.definition.SecretKind;

public record LoginInput(
    String username,
    @SecretField(kind = SecretKind.PASSWORD) String passwordReference,
    String productName,
    String firstName,
    String lastName,
    String postalCode) {}
