# JMS adapter and EMS compatibility

`taf-messaging-jms` compiles against Jakarta JMS 3.1 and does not include a broker or proprietary provider. Enable it with `taf.messaging.jms.enabled=true` and supply one `JmsConnectionFactoryProvider` bean. The provider creates a `jakarta.jms.ConnectionFactory` for each named controller; credentials must be resolved inside that deterministic provider boundary and must not be placed in properties, diagnostics, or evidence.

The controller supports queues, topics, JMS selector expressions, and durable topic subscriptions. A durable controller requires a stable, unique `client-id`. Session cleanup unsubscribes every durable subscription created by that controller and is idempotent.

## EMS profile

TIBCO EMS verification is external because no proprietary jar, image, license, or runtime is distributed with TAF. An EMS profile must provide its Jakarta JMS 3.1-compatible `ConnectionFactory` through the SPI and verify:

- provider client supports the `jakarta.jms` namespace and Java 25;
- queue and topic creation conventions are authorized;
- selector syntax and property conversion match EMS behavior;
- client IDs are unique across parallel sessions;
- durable create, offline delivery, reconnect, unsubscribe, and repeated cleanup pass;
- TLS/cipher/trust configuration is supplied outside model-visible configuration;
- reconnect, failover, acknowledgment, and provider limits are documented;
- provider exceptions and metadata do not expose endpoints, usernames, credentials, or payloads.

The bundled container profile uses Apache ActiveMQ Artemis only as the open-source JMS compatibility baseline. Passing it does not claim EMS certification. EMS-specific failover, shared durable consumers, administered JNDI objects, XA transactions, and vendor management APIs are outside MSG-004 and must be recorded by the external compatibility profile.
