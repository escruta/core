# Agent Guidelines and Considerations

When addressing any task in the `@core/` backend, you must thoroughly research how similar problems have been resolved
in other components across the codebase. For instance, always check existing implementations like
`NotebookController.java` and `NotebookService.java` before attempting to resolve a related issue.

The backend is built using Java 21, Spring Boot framework, and Gradle. You must strictly adhere to the established
layered architecture, separating concerns into Controllers, Services, and Repositories.

Dependency injection must always be performed using constructor injection. Use Lombok's `@RequiredArgsConstructor`
on your classes and declare dependencies as `private final` fields. The use of field injection with `@Autowired` is
prohibited.

For all data coming into and going out of the API, you must use Data Transfer Objects (DTOs). These DTOs must be
implemented as Java `record`s. Input validation is mandatory and should be enforced by placing Jakarta Validation
annotations (such as `@NotNull`, `@NotBlank`, or `@UUID`) directly on the fields of the DTO records, and using the
`@Valid` annotation on the `@RequestBody` in the controller.

Controllers should return either the DTO directly (which defaults to a 200 OK status) or a `ResponseEntity<DTO>` when a
specific HTTP status code is required (e.g., `HttpStatus.CREATED` for successful creations or `HttpStatus.NOT_FOUND`
when a resource is missing). Entities must never be returned directly from controllers; they must always be mapped to a
response DTO either via a dedicated Mapper class or through the DTO's constructor.

Security and data ownership are critical. When implementing endpoints that access, modify, or delete resources, you must
ensure the current user owns the resource. This should be achieved by applying the `@PreAuthorize` annotation on the
controller methods, referencing the appropriate ownership service (e.g.,
`@PreAuthorize("@notebookOwnershipService.isUserNotebookOwner(#id)")`).

When retrieving entities by their identifier, use `Optional` in the Service layer to gracefully handle cases where the
entity does not exist. If a requested entity is not found during a read, update, or delete operation, the service should
handle it appropriately (e.g., returning `null` or `Optional.empty()`), and the controller must respond with a
`404` status.

For operations that modify the database, especially those involving multiple steps or publishing domain events (via
`ApplicationEventPublisher`), annotate the service method with Spring's `@Transactional` to guarantee atomicity and data
consistency.
