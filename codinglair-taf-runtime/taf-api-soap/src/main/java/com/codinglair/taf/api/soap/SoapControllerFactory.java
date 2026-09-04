package com.codinglair.taf.api.soap;

@FunctionalInterface
public interface SoapControllerFactory {
  SoapController create(String name);
}
