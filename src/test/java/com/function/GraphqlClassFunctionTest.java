package com.function;

import com.microsoft.azure.functions.ExecutionContext;
import com.microsoft.azure.functions.HttpRequestMessage;
import com.microsoft.azure.functions.HttpResponseMessage;
import com.microsoft.azure.functions.HttpStatus;
import org.junit.jupiter.api.Test;
import org.mockito.invocation.InvocationOnMock;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.logging.Logger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;

public class GraphqlClassFunctionTest {
    @Test
    public void testGraphqlQueryFromBodyReturnsData() {
        HttpRequestMessage<Optional<String>> request = crearRequest(
            Map.of(),
            Optional.of("""
                {
                  "query": "{ cursos { nombre nivel profesor { nombre } alumnos { nombre edad } } }"
                }
                """)
        );
        ExecutionContext context = crearContexto();

        HttpResponseMessage response = new GraphqlClassFunction().run(request, context);

        assertEquals(HttpStatus.OK, response.getStatus());
        assertTrue(response.getBody().toString().contains("\"data\""));
        assertTrue(response.getBody().toString().contains("Introduccion a GraphQL"));
        assertTrue(response.getBody().toString().contains("\"Ana Ruiz\""));
    }

    @Test
    public void testInvalidGraphqlFieldReturnsBadRequest() {
        HttpRequestMessage<Optional<String>> request = crearRequest(
            Map.of("query", "{ cursos { sala } }"),
            Optional.empty()
        );
        ExecutionContext context = crearContexto();

        HttpResponseMessage response = new GraphqlClassFunction().run(request, context);

        assertEquals(HttpStatus.BAD_REQUEST, response.getStatus());
        assertTrue(response.getBody().toString().contains("\"errors\""));
        assertTrue(response.getBody().toString().contains("sala"));
    }

    @SuppressWarnings("unchecked")
    private HttpRequestMessage<Optional<String>> crearRequest(
        Map<String, String> queryParams,
        Optional<String> body
    ) {
        HttpRequestMessage<Optional<String>> request = mock(HttpRequestMessage.class);
        doReturn(new HashMap<>(queryParams)).when(request).getQueryParameters();
        doReturn(body).when(request).getBody();

        doAnswer((InvocationOnMock invocation) -> {
            HttpStatus status = (HttpStatus) invocation.getArguments()[0];
            return new HttpResponseMessageMock.HttpResponseMessageBuilderMock().status(status);
        }).when(request).createResponseBuilder(any(HttpStatus.class));

        return request;
    }

    private ExecutionContext crearContexto() {
        ExecutionContext context = mock(ExecutionContext.class);
        doReturn(Logger.getGlobal()).when(context).getLogger();
        return context;
    }
}
