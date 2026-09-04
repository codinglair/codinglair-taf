package com.codinglair.taf.api.rest;

@FunctionalInterface
public interface RestControllerFactory {
  RestController create(String name);
}
