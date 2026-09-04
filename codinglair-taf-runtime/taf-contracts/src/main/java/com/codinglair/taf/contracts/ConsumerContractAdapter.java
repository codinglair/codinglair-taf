package com.codinglair.taf.contracts;

/** Boundary for Pact or another approved consumer-contract provider. */
public interface ConsumerContractAdapter extends ContractAdapter {
  @Override
  default ContractFormat format() {
    return ContractFormat.CONSUMER;
  }
}
