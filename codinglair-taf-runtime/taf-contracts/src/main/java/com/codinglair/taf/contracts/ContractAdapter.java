package com.codinglair.taf.contracts;

/** Specialized contract tools plug in here without owning REST or messaging execution. */
public interface ContractAdapter {
  String provider();

  ContractFormat format();

  ContractValidationResult validate(ContractDocument document);

  ContractScaffoldResult scaffold(ContractScaffoldRequest request);
}
