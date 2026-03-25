package com.function;

import com.microsoft.azure.functions.ExecutionContext;
import com.microsoft.azure.functions.HttpRequestMessage;
import com.microsoft.azure.functions.HttpResponseMessage;
import com.microsoft.azure.functions.HttpStatus;
import org.junit.jupiter.api.Test;
import org.mockito.invocation.InvocationOnMock;

import java.io.IOException;
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

/**
 * Unit test for Function class.
 */
public class FunctionTest {
    @Test
    public void testHttpTriggerJavaSuccess() throws Exception {
        HttpRequestMessage<Optional<String>> req = crearRequest(Map.of(
            "pesos", "100000",
            "indicador", "uf"
        ));
        ExecutionContext context = crearContexto();

        HttpResponseMessage ret = new StubFunction("""
            {
              "version":"1.7.0",
              "autor":"mindicador.cl",
              "codigo":"uf",
              "nombre":"Unidad de fomento (UF)",
              "unidad_medida":"Pesos",
              "serie":[
                {
                  "fecha":"2026-03-25T03:00:00.000Z",
                  "valor":40000.0
                }
              ]
            }
            """).run(req, context);

        assertEquals(HttpStatus.OK, ret.getStatus());
        assertTrue(ret.getBody().toString().contains("\"resultado\": 2.5000"));
        assertTrue(ret.getBody().toString().contains("\"indicador\": \"uf\""));
    }

    @Test
    public void testInvalidPesosReturnsBadRequest() throws Exception {
        HttpRequestMessage<Optional<String>> req = crearRequest(Map.of(
            "pesos", "abc",
            "indicador", "uf"
        ));
        ExecutionContext context = crearContexto();

        HttpResponseMessage ret = new Function().run(req, context);

        assertEquals(HttpStatus.BAD_REQUEST, ret.getStatus());
        assertTrue(ret.getBody().toString().contains("debe ser numerico"));
    }

    @Test
    public void testApiErrorReturnsBadGateway() throws Exception {
        HttpRequestMessage<Optional<String>> req = crearRequest(Map.of(
            "pesos", "100000",
            "indicador", "uf"
        ));
        ExecutionContext context = crearContexto();

        HttpResponseMessage ret = new StubFunction(new IOException("timeout")).run(req, context);

        assertEquals(HttpStatus.BAD_GATEWAY, ret.getStatus());
        assertTrue(ret.getBody().toString().contains("mindicador.cl"));
    }

    @SuppressWarnings("unchecked")
    private HttpRequestMessage<Optional<String>> crearRequest(Map<String, String> queryParams) {
        HttpRequestMessage<Optional<String>> req = mock(HttpRequestMessage.class);
        doReturn(new HashMap<>(queryParams)).when(req).getQueryParameters();
        doReturn(Optional.empty()).when(req).getBody();

        doAnswer((InvocationOnMock invocation) -> {
            HttpStatus status = (HttpStatus) invocation.getArguments()[0];
            return new HttpResponseMessageMock.HttpResponseMessageBuilderMock().status(status);
        }).when(req).createResponseBuilder(any(HttpStatus.class));

        return req;
    }

    private ExecutionContext crearContexto() {
        ExecutionContext context = mock(ExecutionContext.class);
        doReturn(Logger.getGlobal()).when(context).getLogger();
        return context;
    }

    private static class StubFunction extends Function {
        private final String responseBody;
        private final IOException ioException;

        private StubFunction(String responseBody) {
            this.responseBody = responseBody;
            this.ioException = null;
        }

        private StubFunction(IOException ioException) {
            this.responseBody = null;
            this.ioException = ioException;
        }

        @Override
        protected String consultarMindicador(String indicador) throws IOException {
            if (ioException != null) {
                throw ioException;
            }

            return responseBody;
        }
    }
}
