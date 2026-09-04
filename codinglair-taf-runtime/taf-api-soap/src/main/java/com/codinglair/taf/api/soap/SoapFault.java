package com.codinglair.taf.api.soap;

public record SoapFault(String code, String reason, String detail) {}
