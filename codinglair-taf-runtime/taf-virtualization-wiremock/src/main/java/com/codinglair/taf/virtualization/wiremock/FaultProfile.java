package com.codinglair.taf.virtualization.wiremock;

import com.github.tomakehurst.wiremock.http.Fault;
import java.time.Duration;
import java.util.Objects;

/** Governed response degradation applied to one session-scoped mapping. */
public sealed interface FaultProfile
    permits FaultProfile.None, FaultProfile.Latency, FaultProfile.Network {
  record None() implements FaultProfile {}

  record Latency(Duration delay) implements FaultProfile {
    public Latency {
      Objects.requireNonNull(delay, "delay");
      if (delay.isNegative() || delay.isZero() || delay.toMillis() > Integer.MAX_VALUE) {
        throw new IllegalArgumentException("Latency must be positive and fit in milliseconds");
      }
    }
  }

  record Network(NetworkFault fault) implements FaultProfile {
    public Network {
      Objects.requireNonNull(fault, "fault");
    }
  }

  enum NetworkFault {
    EMPTY_RESPONSE(Fault.EMPTY_RESPONSE),
    CONNECTION_RESET(Fault.CONNECTION_RESET_BY_PEER),
    MALFORMED_RESPONSE(Fault.MALFORMED_RESPONSE_CHUNK);
    private final Fault wireMockFault;

    NetworkFault(Fault wireMockFault) {
      this.wireMockFault = wireMockFault;
    }

    Fault wireMockFault() {
      return wireMockFault;
    }
  }

  static FaultProfile none() {
    return new None();
  }

  static FaultProfile latency(Duration delay) {
    return new Latency(delay);
  }

  static FaultProfile network(NetworkFault fault) {
    return new Network(fault);
  }
}
