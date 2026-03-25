package com.function;

import com.microsoft.azure.functions.ExecutionContext;
import com.microsoft.azure.functions.HttpMethod;
import com.microsoft.azure.functions.HttpRequestMessage;
import com.microsoft.azure.functions.HttpResponseMessage;
import com.microsoft.azure.functions.HttpStatus;
import com.microsoft.azure.functions.annotation.AuthorizationLevel;
import com.microsoft.azure.functions.annotation.FunctionName;
import com.microsoft.azure.functions.annotation.HttpTrigger;

import java.io.IOException;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Azure Functions with HTTP Trigger.
 */
public class Function {
    private static final String API_BASE_URL = "https://mindicador.cl/api/";
    private static final Set<String> SUPPORTED_INDICATORS = Set.of(
        "uf",
        "dolar",
        "euro",
        "utm",
        "ivp",
        "dolar_intercambio"
    );
    private static final Pattern NAME_PATTERN = Pattern.compile("\"nombre\"\\s*:\\s*\"([^\"]+)\"");
    private static final Pattern UNIT_PATTERN = Pattern.compile("\"unidad_medida\"\\s*:\\s*\"([^\"]+)\"");
    private static final Pattern SERIE_PATTERN = Pattern.compile(
        "\"serie\"\\s*:\\s*\\[\\s*\\{[^}]*?\"fecha\"\\s*:\\s*\"([^\"]+)\"[^}]*?\"valor\"\\s*:\\s*([0-9]+(?:\\.[0-9]+)?)",
        Pattern.DOTALL
    );

    private final HttpClient httpClient;

    public Function() {
        this(HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(5))
            .build());
    }

    Function(HttpClient httpClient) {
        this.httpClient = httpClient;
    }

    /**
     * Example:
     * GET /api/HttpExample?pesos=1000000&indicador=uf
     */
    @FunctionName("HttpExample")
    public HttpResponseMessage run(
            @HttpTrigger(
                name = "req",
                methods = {HttpMethod.GET, HttpMethod.POST},
                authLevel = AuthorizationLevel.ANONYMOUS)
                HttpRequestMessage<Optional<String>> request,
            final ExecutionContext context) {
        context.getLogger().info("Calculating conversion using mindicador.cl");

        String indicador = obtenerIndicadorSolicitado(request);
        String pesosTexto = request.getQueryParameters().get("pesos");

        if (!SUPPORTED_INDICATORS.contains(indicador)) {
            return crearRespuestaError(
                request,
                HttpStatus.BAD_REQUEST,
                "El parametro 'indicador' no es valido. Use uno de: " + String.join(", ", SUPPORTED_INDICATORS)
            );
        }

        if (pesosTexto == null || pesosTexto.isBlank()) {
            return crearRespuestaError(
                request,
                HttpStatus.BAD_REQUEST,
                "El parametro 'pesos' es obligatorio. Ejemplo: ?pesos=100000&indicador=uf"
            );
        }

        try {
            BigDecimal pesos = parsearPesos(pesosTexto);
            IndicadorData indicadorData = obtenerIndicadorDesdeApi(indicador);
            BigDecimal resultado = calcularEquivalencia(pesos, indicadorData.valor());

            return request.createResponseBuilder(HttpStatus.OK)
                .header("Content-Type", "application/json")
                .body(construirRespuestaExitosa(pesos, indicador, indicadorData, resultado))
                .build();
        } catch (IllegalArgumentException e) {
            return crearRespuestaError(request, HttpStatus.BAD_REQUEST, e.getMessage());
        } catch (IOException e) {
            context.getLogger().warning("Error calling mindicador.cl: " + e.getMessage());
            return crearRespuestaError(
                request,
                HttpStatus.BAD_GATEWAY,
                "No fue posible consultar mindicador.cl en este momento."
            );
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            context.getLogger().warning("Request interrupted while calling mindicador.cl");
            return crearRespuestaError(
                request,
                HttpStatus.INTERNAL_SERVER_ERROR,
                "La consulta fue interrumpida antes de completarse."
            );
        } catch (IllegalStateException e) {
            context.getLogger().warning("Unexpected API response: " + e.getMessage());
            return crearRespuestaError(
                request,
                HttpStatus.BAD_GATEWAY,
                "La API devolvio una respuesta inesperada."
            );
        }
    }

    private String obtenerIndicadorSolicitado(HttpRequestMessage<Optional<String>> request) {
        return Optional.ofNullable(request.getQueryParameters().get("indicador"))
            .map(String::trim)
            .filter(valor -> !valor.isEmpty())
            .map(valor -> valor.toLowerCase(Locale.ROOT))
            .orElse("uf");
    }

    private BigDecimal parsearPesos(String pesosTexto) {
        String normalizado = pesosTexto.trim().replace(",", ".");
        BigDecimal pesos;

        try {
            pesos = new BigDecimal(normalizado);
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("El parametro 'pesos' debe ser numerico.");
        }

        if (pesos.compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException("El parametro 'pesos' debe ser mayor que 0.");
        }

        return pesos.setScale(2, RoundingMode.HALF_UP);
    }

    protected IndicadorData obtenerIndicadorDesdeApi(String indicador) throws IOException, InterruptedException {
        String respuesta = consultarMindicador(indicador);
        return parsearIndicador(respuesta);
    }

    protected String consultarMindicador(String indicador) throws IOException, InterruptedException {
        HttpRequest request = HttpRequest.newBuilder()
            .uri(URI.create(API_BASE_URL + URLEncoder.encode(indicador, StandardCharsets.UTF_8)))
            .timeout(Duration.ofSeconds(10))
            .header("Accept", "application/json")
            .GET()
            .build();

        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

        if (response.statusCode() != 200) {
            throw new IOException("mindicador.cl responded with status " + response.statusCode());
        }

        return response.body();
    }

    private IndicadorData parsearIndicador(String json) {
        String nombre = extraerTexto(json, NAME_PATTERN, "nombre");
        String unidad = extraerTexto(json, UNIT_PATTERN, "unidad_medida");

        Matcher serieMatcher = SERIE_PATTERN.matcher(json);
        if (!serieMatcher.find()) {
            throw new IllegalStateException("No se pudo extraer 'fecha' y 'valor' desde la respuesta.");
        }

        return new IndicadorData(
            nombre,
            unidad,
            serieMatcher.group(1),
            new BigDecimal(serieMatcher.group(2))
        );
    }

    private String extraerTexto(String json, Pattern pattern, String campo) {
        Matcher matcher = pattern.matcher(json);
        if (!matcher.find()) {
            throw new IllegalStateException("No se pudo extraer '" + campo + "' desde la respuesta.");
        }

        return matcher.group(1);
    }

    private BigDecimal calcularEquivalencia(BigDecimal pesos, BigDecimal valorIndicador) {
        return pesos.divide(valorIndicador, 4, RoundingMode.HALF_UP);
    }

    private String construirRespuestaExitosa(
        BigDecimal pesos,
        String indicador,
        IndicadorData indicadorData,
        BigDecimal resultado
    ) {
        return """
            {
              "mensaje": "Calculo realizado correctamente",
              "indicador": "%s",
              "nombreIndicador": "%s",
              "unidadMedida": "%s",
              "montoPesos": %s,
              "valorIndicador": %s,
              "resultado": %s,
              "fechaIndicador": "%s",
              "fuente": "%s%s"
            }
            """.formatted(
            escaparJson(indicador),
            escaparJson(indicadorData.nombre()),
            escaparJson(indicadorData.unidadMedida()),
            pesos.toPlainString(),
            indicadorData.valor().setScale(2, RoundingMode.HALF_UP).toPlainString(),
            resultado.toPlainString(),
            escaparJson(indicadorData.fecha()),
            API_BASE_URL,
            escaparJson(indicador)
        );
    }

    private HttpResponseMessage crearRespuestaError(
        HttpRequestMessage<Optional<String>> request,
        HttpStatus status,
        String mensaje
    ) {
        return request.createResponseBuilder(status)
            .header("Content-Type", "application/json")
            .body("""
                {
                  "error": "%s"
                }
                """.formatted(escaparJson(mensaje)))
            .build();
    }

    private String escaparJson(String texto) {
        return texto
            .replace("\\", "\\\\")
            .replace("\"", "\\\"");
    }

    protected record IndicadorData(
        String nombre,
        String unidadMedida,
        String fecha,
        BigDecimal valor
    ) {
    }
}
