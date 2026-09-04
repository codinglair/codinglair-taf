package com.codinglair.taf.runtime.definition;

/** Provider-neutral access to externally stored immutable payloads. */
public interface PayloadResolver {
  ResolvedPayload open(PayloadReference reference);

  ResolvedPayload open(PayloadReference reference, PayloadRange range);
}
