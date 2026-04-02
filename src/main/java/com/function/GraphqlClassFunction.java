package com.function;

import com.function.graphql.ClassGraphqlProvider;
import com.function.graphql.JsonUtils;
import com.microsoft.azure.functions.ExecutionContext;
import com.microsoft.azure.functions.HttpMethod;
import com.microsoft.azure.functions.HttpRequestMessage;
import com.microsoft.azure.functions.HttpResponseMessage;
import com.microsoft.azure.functions.HttpStatus;
import com.microsoft.azure.functions.annotation.AuthorizationLevel;
import com.microsoft.azure.functions.annotation.FunctionName;
import com.microsoft.azure.functions.annotation.HttpTrigger;
import graphql.ExecutionResult;

import java.util.Map;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class GraphqlClassFunction {
    private static final Pattern QUERY_PATTERN = Pattern.compile(
        "\"query\"\\s*:\\s*\"((?:\\\\.|[^\"])*)\"",
        Pattern.DOTALL
    );

    private final ClassGraphqlProvider graphQLProvider;

    public GraphqlClassFunction() {
        this(new ClassGraphqlProvider());
    }

    GraphqlClassFunction(ClassGraphqlProvider graphQLProvider) {
        this.graphQLProvider = graphQLProvider;
    }

    /**
     * Example:
     * POST /api/GraphqlClassExample
     * {"query":"{ cursos { nombre nivel profesor { nombre } alumnos { nombre edad } } }"}
     */
    @FunctionName("GraphqlClassExample")
    public HttpResponseMessage run(
        @HttpTrigger(
            name = "req",
            methods = {HttpMethod.GET, HttpMethod.POST},
            authLevel = AuthorizationLevel.ANONYMOUS
        )
        HttpRequestMessage<Optional<String>> request,
        final ExecutionContext context
    ) {
        context.getLogger().info("Executing GraphQL classroom example");

        String query = obtenerQuery(request);
        if (query == null || query.isBlank()) {
            return request.createResponseBuilder(HttpStatus.BAD_REQUEST)
                .header("Content-Type", "application/json")
                .body(JsonUtils.toJson(Map.of(
                    "error", "Debes enviar una consulta GraphQL en el parametro 'query' o en el body.",
                    "ejemplo", "{ cursos { nombre nivel profesor { nombre } alumnos { nombre edad } } }"
                )))
                .build();
        }

        ExecutionResult result = graphQLProvider.execute(query);
        HttpStatus status = result.getErrors().isEmpty() ? HttpStatus.OK : HttpStatus.BAD_REQUEST;

        return request.createResponseBuilder(status)
            .header("Content-Type", "application/json")
            .body(JsonUtils.toJson(result.toSpecification()))
            .build();
    }

    private String obtenerQuery(HttpRequestMessage<Optional<String>> request) {
        String queryParam = request.getQueryParameters().get("query");
        if (queryParam != null && !queryParam.isBlank()) {
            return queryParam.trim();
        }

        return request.getBody()
            .map(String::trim)
            .filter(body -> !body.isEmpty())
            .map(this::extraerQueryDesdeBody)
            .orElse("");
    }

    private String extraerQueryDesdeBody(String body) {
        if (!body.startsWith("{")) {
            return body;
        }

        Matcher matcher = QUERY_PATTERN.matcher(body);
        if (!matcher.find()) {
            return "";
        }

        return desescaparJson(matcher.group(1)).trim();
    }

    private String desescaparJson(String value) {
        StringBuilder builder = new StringBuilder();

        for (int index = 0; index < value.length(); index++) {
            char current = value.charAt(index);

            if (current == '\\' && index + 1 < value.length()) {
                char escaped = value.charAt(++index);
                switch (escaped) {
                    case '"':
                        builder.append('"');
                        break;
                    case '\\':
                        builder.append('\\');
                        break;
                    case '/':
                        builder.append('/');
                        break;
                    case 'b':
                        builder.append('\b');
                        break;
                    case 'f':
                        builder.append('\f');
                        break;
                    case 'n':
                        builder.append('\n');
                        break;
                    case 'r':
                        builder.append('\r');
                        break;
                    case 't':
                        builder.append('\t');
                        break;
                    default:
                        builder.append(escaped);
                        break;
                }
            } else {
                builder.append(current);
            }
        }

        return builder.toString();
    }
}
