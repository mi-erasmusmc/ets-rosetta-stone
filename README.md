eTransafe Rosetta Stone
=======================

**The eTransafe Rosetta Stone is a Spring Boot application that exposes API endpoints for the translation between
clinical and preclinical terminologies, normalization and lookup of terms, as well as hierarchical expansions of
concepts. It was developed at the Erasmus Medical Centre department of Medical Informatics as part of the eTransafe
project**

### REQUIREMENTS

- [Java 17+]("https://www.oracle.com/java/technologies/downloads/")
- Maven
- MySQL with the eTransafe Rosetta Stone database, can be done
  with [this code](https://github.com/mi-erasmusmc/ets-rosetta-stone-database)

### HOW TO USE

- Make sure you have the eTransafe Rosetta Stone database up and running
- Configure your database settings in the application.yml in the ./src/main/resources folder or set the relevant
  properties as environment variables
- Run `mvn spring-boot:run`
- When the app is running the API docs are exposed at http://localhost:8081/v2/swagger-ui/index.html#/

**Note**: The eTransafe Rosetta Stone has been developed with macOS and has been used extensively running in an Alpine
container on Kubernetes. No testing has been done on any other operating systems.

### ADDITIONAL OPTIONS

#### Authentication

It is possible to use the eTransafe Rosetta Stone in combination with Keycloak. To enable this set the environment
variables auth.enabled = true and configure the toxhub.auth.url variable to provide the keycloak url

#### Caching

If you are planning heavy lifting with the eTransafe Rosetta Stone it is recommended you enable caching. At the
present the caching has been optimized for use by the Sirona app which requests 1000s of translations at a time. You may
find you need to be caching elsewhere for your specific usage. To enable caching you need to have a Redis instance that
the app can connect to. You can for example run one in a docker container locally
with `docker run -d -p 6379:6379 redis:7`. In addition, you need to remove spring.cache.type=NONE or set it to REDIS in
the application.yml (or set it as environment variable)

#### UI

There is a UI available for end users to view translation between terminologies [here](https://github.com/mi-erasmusmc/ets-rosetta-stone-ui)

#### Docker

The Dockerfile in this repo runs the eTransafe Rosetta Stone in an Alpine container.

### PUBLICATION

We are in the final stages of completing a publication about the eTransafe Rosetta Stone detailing its workings.

### CONTRIBUTIONS

Contributions are welcome, please get in touch.

