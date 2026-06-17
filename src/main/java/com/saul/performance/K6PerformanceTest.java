package com.saul.performance;

import com.google.gson.*;
import org.apache.commons.exec.CommandLine;
import org.apache.commons.exec.DefaultExecutor;
import org.apache.commons.exec.ExecuteException;
import org.apache.commons.exec.PumpStreamHandler;
import org.apache.commons.io.FileUtils;
import org.testng.annotations.Test;

import java.io.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.concurrent.*;
import java.util.stream.Collectors;

public class K6PerformanceTest {

    private static final String K6_SCRIPTS_DIR = "k6_tests/scripts";
    private static final String RESULTS_DIR = "results";
    private static final String RESULTS_JSON = "results.json";
    private static final String RESULTS_HTML = "results.html";
    private static final Gson gson = new GsonBuilder().setPrettyPrinting().create();

    @Test(description = "Ejecutar todos los scripts k6 en paralelo y consolidar resultados en HTML")
    public void runAllK6ScriptsInParallel() throws Exception {
        System.out.println("========== Iniciando ejecución de todos los scripts k6 ==========");

        // Crear directorio de resultados si no existe
        Files.createDirectories(Paths.get(RESULTS_DIR));

        // Obtener lista de scripts .js en k6_tests/scripts
        List<Path> scriptFiles = getK6Scripts();
        if (scriptFiles.isEmpty()) {
            throw new Exception("No se encontraron scripts .js en " + K6_SCRIPTS_DIR);
        }

        System.out.println("Scripts encontrados: " + scriptFiles.size());
        scriptFiles.forEach(p -> System.out.println("  - " + p.getFileName()));

        // Ejecutar scripts en paralelo
        Map<String, String> scriptResults = executeScriptsInParallel(scriptFiles);

        // Consolidar resultados
        JsonObject consolidatedResults = consolidateResults(scriptResults);

        // Generar HTML
        String htmlReport = generateHTMLReport(consolidatedResults);

        // Guardar resultados
        String resultsJsonPath = RESULTS_DIR + "/" + RESULTS_JSON;
        String resultsHtmlPath = RESULTS_HTML;

        FileUtils.writeStringToFile(new File(resultsJsonPath), gson.toJson(consolidatedResults), "UTF-8");
        FileUtils.writeStringToFile(new File(resultsHtmlPath), htmlReport, "UTF-8");

        System.out.println("========== Ejecución completada ==========");
        System.out.println("Resultados JSON: " + new File(resultsJsonPath).getAbsolutePath());
        System.out.println("Resultados HTML: " + new File(resultsHtmlPath).getAbsolutePath());
    }

    /**
     * Obtiene lista de scripts .js en el directorio de scripts k6
     */
    private List<Path> getK6Scripts() throws IOException {
        Path scriptsPath = Paths.get(K6_SCRIPTS_DIR);
        if (!Files.exists(scriptsPath)) {
            throw new IOException("Directorio de scripts no encontrado: " + scriptsPath.toAbsolutePath());
        }

        // Excluir reporter.js y otros archivos que no sean scripts k6 reales
        return Files.list(scriptsPath)
                .filter(p -> p.toString().endsWith(".js"))
                .filter(p -> !p.getFileName().toString().equals("reporter.js"))
                .filter(p -> !p.getFileName().toString().equals("package.json"))
                .collect(Collectors.toList());
    }

    /**
     * Ejecuta todos los scripts en paralelo
     */
    private Map<String, String> executeScriptsInParallel(List<Path> scriptFiles) throws InterruptedException {
        ExecutorService executor = Executors.newFixedThreadPool(scriptFiles.size());
        Map<String, Future<String>> futures = new HashMap<>();

        // Enviar cada script a ejecutar
        for (Path scriptFile : scriptFiles) {
            String scriptName = scriptFile.getFileName().toString();
            futures.put(scriptName, executor.submit(() -> executeK6Script(scriptFile)));
        }

        // Esperar a que terminen todos
        Map<String, String> results = new HashMap<>();
        for (Map.Entry<String, Future<String>> entry : futures.entrySet()) {
            try {
                String jsonOutput = entry.getValue().get(5, TimeUnit.MINUTES);
                results.put(entry.getKey(), jsonOutput);
                System.out.println("✓ Script completado: " + entry.getKey());
            } catch (TimeoutException e) {
                System.err.println("✗ Script expirado (timeout): " + entry.getKey());
                results.put(entry.getKey(), "{}"); // Resultado vacío
            } catch (ExecutionException e) {
                System.err.println("✗ Error en script " + entry.getKey() + ": " + e.getMessage());
                results.put(entry.getKey(), "{}");
            }
        }

        executor.shutdown();
        return results;
    }

    /**
     * Ejecuta un script k6 individual y retorna su salida JSON
     */
    private String executeK6Script(Path scriptPath) throws Exception {
        String resultJsonFile = RESULTS_DIR + "/" + scriptPath.getFileName().toString().replace(".js", ".json");

        // Comando k6 con salida JSON en formato NDJSON
        String command = "k6 run --out json=" + resultJsonFile + " " + scriptPath.toString();

        System.out.println("Ejecutando: " + command);

        ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
        PumpStreamHandler streamHandler = new PumpStreamHandler(outputStream);

        CommandLine cmdLine = CommandLine.parse(command);
        DefaultExecutor executor = new DefaultExecutor();
        executor.setStreamHandler(streamHandler);

        int exitValue = executor.execute(cmdLine);

        if (exitValue != 0) {
            System.err.println("k6 ejecutado con código de salida: " + exitValue + " para " + scriptPath.getFileName());
        }

        // Leer el archivo JSON generado (NDJSON) y procesarlo
        if (Files.exists(Paths.get(resultJsonFile))) {
            String ndjsonContent = FileUtils.readFileToString(new File(resultJsonFile), "UTF-8");
            return processNDJSON(ndjsonContent);
        }

        return "{}";
    }

    /**
     * Procesa NDJSON de k6 y lo convierte a estructura consolidada
     */
    private String processNDJSON(String ndjsonContent) {
        JsonObject consolidatedMetrics = new JsonObject();
        Map<String, JsonObject> metrics = new HashMap<>();

        String[] lines = ndjsonContent.split("\n");
        for (String line : lines) {
            if (line.trim().isEmpty()) continue;

            try {
                com.google.gson.stream.JsonReader jsonReader = new com.google.gson.stream.JsonReader(
                        new java.io.StringReader(line));
                jsonReader.setLenient(true);
                JsonObject obj = JsonParser.parseReader(jsonReader).getAsJsonObject();

                // Procesar métrica
                if (obj.has("type") && obj.get("type").getAsString().equals("Metric")) {
                    JsonObject data = obj.getAsJsonObject("data");
                    String metricName = data.get("name").getAsString();
                    
                    JsonObject metric = new JsonObject();
                    metric.addProperty("type", data.get("type").getAsString());
                    metric.add("values", new JsonObject()); // Inicializar values vacío
                    metrics.put(metricName, metric);
                } 
                // Procesar puntos de datos
                else if (obj.has("type") && obj.get("type").getAsString().equals("Point")) {
                    String metricName = obj.get("metric").getAsString();
                    JsonObject data = obj.getAsJsonObject("data");
                    JsonElement value = data.get("value");

                    if (!metrics.containsKey(metricName)) {
                        JsonObject metric = new JsonObject();
                        metric.addProperty("type", "counter");
                        metric.add("values", new JsonObject());
                        metrics.put(metricName, metric);
                    }

                    // Acumular valores
                    JsonObject metricObj = metrics.get(metricName);
                    if (value != null && value.isJsonPrimitive()) {
                        metricObj.getAsJsonObject("values").add(metricName + "_point", value.getAsJsonPrimitive());
                    }
                }
            } catch (Exception e) {
                // Ignorar líneas malformadas
            }
        }

        // Construir objeto final con estructura esperada
        JsonObject result = new JsonObject();
        result.addProperty("type", "summary");
        
        JsonObject metricsObj = new JsonObject();
        for (Map.Entry<String, JsonObject> entry : metrics.entrySet()) {
            metricsObj.add(entry.getKey(), entry.getValue());
        }
        result.add("metrics", metricsObj);

        return result.toString();
    }

    /**
     * Consolida resultados de múltiples ejecuciones k6
     */
    private JsonObject consolidateResults(Map<String, String> scriptResults) {
        JsonObject consolidated = new JsonObject();
        consolidated.addProperty("timestamp", LocalDateTime.now().format(DateTimeFormatter.ISO_DATE_TIME));
        consolidated.addProperty("totalScripts", scriptResults.size());

        JsonObject metricsAggregate = new JsonObject();
        JsonArray scriptReports = new JsonArray();
        int totalRequests = 0;
        int totalFailed = 0;
        int totalChecks = 0;
        int totalChecksFailed = 0;

        for (Map.Entry<String, String> entry : scriptResults.entrySet()) {
            String scriptName = entry.getKey();
            String jsonContent = entry.getValue();

            try {
                // Usar JsonReader con lenient mode para JSON malformado
                com.google.gson.stream.JsonReader jsonReader = new com.google.gson.stream.JsonReader(
                        new java.io.StringReader(jsonContent));
                jsonReader.setLenient(true);

                JsonElement element = JsonParser.parseReader(jsonReader);
                if (!element.isJsonObject()) {
                    System.err.println("⚠ JSON no es un objeto en " + scriptName);
                    continue;
                }

                JsonObject scriptData = element.getAsJsonObject();
                JsonObject report = new JsonObject();
                report.addProperty("script", scriptName);

                // Extraer métricas si contienen "metrics"
                if (scriptData.has("metrics")) {
                    JsonObject metrics = scriptData.getAsJsonObject("metrics");
                    report.add("metrics", metrics);

                    // Agregación de conteos
                    if (metrics.has("http_reqs")) {
                        try {
                            totalRequests += metrics.getAsJsonObject("http_reqs").getAsJsonObject("values").get("count").getAsInt();
                        } catch (Exception e) {
                            System.out.println("⚠ No se pudo extraer http_reqs de " + scriptName);
                        }
                    }
                    if (metrics.has("http_req_failed")) {
                        try {
                            totalFailed += metrics.getAsJsonObject("http_req_failed").getAsJsonObject("values").get("passes").getAsInt();
                        } catch (Exception e) {
                            System.out.println("⚠ No se pudo extraer http_req_failed de " + scriptName);
                        }
                    }
                    if (metrics.has("checks")) {
                        try {
                            JsonObject checksMetric = metrics.getAsJsonObject("checks").getAsJsonObject("values");
                            totalChecks += checksMetric.get("passes").getAsInt();
                            totalChecksFailed += checksMetric.get("fails").getAsInt();
                        } catch (Exception e) {
                            System.out.println("⚠ No se pudo extraer checks de " + scriptName);
                        }
                    }
                }

                scriptReports.add(report);
            } catch (JsonSyntaxException e) {
                System.err.println("✗ Error al parsear JSON de " + scriptName + ": " + e.getMessage());
            } catch (Exception e) {
                System.err.println("✗ Error procesando " + scriptName + ": " + e.getMessage());
            }
        }

        consolidated.add("scripts", scriptReports);
        consolidated.addProperty("totalRequests", totalRequests);
        consolidated.addProperty("totalFailedRequests", totalFailed);
        consolidated.addProperty("totalChecks", totalChecks);
        consolidated.addProperty("totalChecksFailed", totalChecksFailed);

        return consolidated;
    }

    /**
     * Genera reporte HTML consolidado
     */
    private String generateHTMLReport(JsonObject consolidatedData) {
        StringBuilder html = new StringBuilder();

        html.append("<!DOCTYPE html>\n");
        html.append("<html lang=\"es\">\n");
        html.append("<head>\n");
        html.append("  <meta charset=\"UTF-8\">\n");
        html.append("  <meta name=\"viewport\" content=\"width=device-width, initial-scale=1.0\">\n");
        html.append("  <title>Reporte Consolidado - K6 Performance Tests</title>\n");
        html.append("  <link rel=\"stylesheet\" href=\"https://cdnjs.cloudflare.com/ajax/libs/font-awesome/7.0.1/css/all.min.css\">\n");
        html.append("  <style>\n");
        html.append(getHTMLStyles());
        html.append("  </style>\n");
        html.append("</head>\n");
        html.append("<body>\n");
        html.append("  <div class=\"container\">\n");
        html.append("    <header>\n");
        html.append("      <h1><i class=\"fas fa-chart-line\"></i> Reporte Consolidado K6 - Performance Testing</h1>\n");
        html.append("      <p>Ejecutado: ").append(consolidatedData.has("timestamp") ? consolidatedData.get("timestamp") : "N/A").append("</p>\n");
        html.append("    </header>\n");
        html.append("    <div class=\"content\">\n");

        // Resumen de métricas
        html.append("      <section class=\"metrics\">\n");

        int totalScripts = consolidatedData.has("totalScripts") ? consolidatedData.get("totalScripts").getAsInt() : 0;
        html.append("        <div class=\"metric-card primary\">\n");
        html.append("          <h3>Total de Scripts</h3>\n");
        html.append("          <div class=\"metric-value\">").append(totalScripts).append("</div>\n");
        html.append("        </div>\n");

        int totalRequests = consolidatedData.has("totalRequests") ? consolidatedData.get("totalRequests").getAsInt() : 0;
        int totalFailed = consolidatedData.has("totalFailedRequests") ? consolidatedData.get("totalFailedRequests").getAsInt() : 0;
        String statusRequests = totalFailed == 0 ? "success" : "danger";

        html.append("        <div class=\"metric-card ").append(statusRequests).append("\">\n");
        html.append("          <h3>Total de Requests</h3>\n");
        html.append("          <div class=\"metric-value\">").append(totalRequests).append("</div>\n");
        html.append("          <p>Fallidos: ").append(totalFailed).append("</p>\n");
        html.append("        </div>\n");

        int totalChecks = consolidatedData.has("totalChecks") ? consolidatedData.get("totalChecks").getAsInt() : 0;
        int checksFailed = consolidatedData.has("totalChecksFailed") ? consolidatedData.get("totalChecksFailed").getAsInt() : 0;
        String statusChecks = checksFailed == 0 ? "success" : "danger";

        html.append("        <div class=\"metric-card ").append(statusChecks).append("\">\n");
        html.append("          <h3>Total de Checks</h3>\n");
        html.append("          <div class=\"metric-value\">").append(totalChecks).append("</div>\n");
        html.append("          <p>Fallidos: ").append(checksFailed).append("</p>\n");
        html.append("        </div>\n");

        html.append("      </section>\n");

        // Tabla de scripts
        html.append("      <section class=\"scripts-table\">\n");
        html.append("        <h2>Resultados por Script</h2>\n");
        html.append("        <table>\n");
        html.append("          <thead>\n");
        html.append("            <tr>\n");
        html.append("              <th>Script</th>\n");
        html.append("              <th>Requests</th>\n");
        html.append("              <th>Failed</th>\n");
        html.append("              <th>Checks</th>\n");
        html.append("              <th>Checks Failed</th>\n");
        html.append("            </tr>\n");
        html.append("          </thead>\n");
        html.append("          <tbody>\n");

        JsonArray scripts = consolidatedData.has("scripts") ? consolidatedData.getAsJsonArray("scripts") : new JsonArray();
        if (scripts != null && scripts.size() > 0) {
            for (JsonElement elem : scripts) {
                JsonObject script = elem.getAsJsonObject();
                String scriptName = script.has("script") ? script.get("script").getAsString() : "N/A";

                int requests = 0;
                int failed = 0;
                int checks = 0;
                int checksFail = 0;

                if (script.has("metrics")) {
                    JsonObject metrics = script.getAsJsonObject("metrics");
                    if (metrics.has("http_reqs")) {
                        try {
                            requests = metrics.getAsJsonObject("http_reqs").getAsJsonObject("values").get("count").getAsInt();
                        } catch (Exception e) {
                            requests = 0;
                        }
                    }
                    if (metrics.has("http_req_failed")) {
                        try {
                            failed = metrics.getAsJsonObject("http_req_failed").getAsJsonObject("values").get("passes").getAsInt();
                        } catch (Exception e) {
                            failed = 0;
                        }
                    }
                    if (metrics.has("checks")) {
                        try {
                            checks = metrics.getAsJsonObject("checks").getAsJsonObject("values").get("passes").getAsInt();
                            checksFail = metrics.getAsJsonObject("checks").getAsJsonObject("values").get("fails").getAsInt();
                        } catch (Exception e) {
                            checks = 0;
                            checksFail = 0;
                        }
                    }
                }

                String statusRow = failed > 0 || checksFail > 0 ? "status-fail" : "status-ok";
                html.append("            <tr class=\"").append(statusRow).append("\">\n");
                html.append("              <td>").append(scriptName).append("</td>\n");
                html.append("              <td>").append(requests).append("</td>\n");
                html.append("              <td>").append(failed).append("</td>\n");
                html.append("              <td>").append(checks).append("</td>\n");
                html.append("              <td>").append(checksFail).append("</td>\n");
                html.append("            </tr>\n");
            }
        } else {
            html.append("            <tr class=\"status-ok\">\n");
            html.append("              <td colspan=\"5\" style=\"text-align: center; padding: 2rem;\">No hay datos de scripts disponibles</td>\n");
            html.append("            </tr>\n");
        }

        html.append("          </tbody>\n");
        html.append("        </table>\n");
        html.append("      </section>\n");

        html.append("    </div>\n");
        html.append("    <footer>\n");
        html.append("      <p>K6 Consolidated Report - Performance Testing Suite</p>\n");
        html.append("    </footer>\n");
        html.append("  </div>\n");
        html.append("</body>\n");
        html.append("</html>\n");

        return html.toString();
    }

    /**
     * Retorna los estilos CSS para el reporte HTML
     */
    private String getHTMLStyles() {
        return "* { margin: 0; padding: 0; box-sizing: border-box; } " +
               "body { font-family: 'Segoe UI', Tahoma, Geneva, Verdana, sans-serif; " +
               "background: linear-gradient(135deg, #667eea 0%, #764ba2 100%); " +
               "min-height: 100vh; padding: 2rem; } " +
               ".container { max-width: 1200px; margin: 0 auto; background: white; " +
               "border-radius: 12px; box-shadow: 0 20px 60px rgba(0, 0, 0, 0.3); overflow: hidden; } " +
               "header { background: linear-gradient(135deg, #7c3aed 0%, #5b21b6 100%); " +
               "color: white; padding: 2rem; text-align: center; } " +
               "header h1 { font-size: 2.5rem; margin-bottom: 0.5rem; display: flex; " +
               "align-items: center; justify-content: center; gap: 1rem; } " +
               "header p { font-size: 1rem; opacity: 0.9; } " +
               ".content { padding: 2rem; } " +
               ".metrics { display: grid; grid-template-columns: repeat(auto-fit, minmax(250px, 1fr)); " +
               "gap: 1.5rem; margin-bottom: 2rem; } " +
               ".metric-card { padding: 1.5rem; border-radius: 12px; color: white; " +
               "box-shadow: 0 4px 6px rgba(0, 0, 0, 0.1); text-align: center; } " +
               ".metric-card.primary { background: linear-gradient(135deg, #667eea 0%, #764ba2 100%); } " +
               ".metric-card.success { background: linear-gradient(135deg, #68d391 0%, #48bb78 100%); } " +
               ".metric-card.danger { background: linear-gradient(135deg, #fc8181 0%, #f56565 100%); } " +
               ".metric-card h3 { font-size: 0.9rem; text-transform: uppercase; opacity: 0.9; " +
               "margin-bottom: 1rem; } " +
               ".metric-value { font-size: 2.5rem; font-weight: 700; margin-bottom: 0.5rem; } " +
               ".metric-card p { font-size: 0.9rem; opacity: 0.9; } " +
               ".scripts-table { margin-top: 2rem; } " +
               ".scripts-table h2 { font-size: 1.5rem; margin-bottom: 1rem; color: #2d3748; " +
               "border-bottom: 2px solid #e2e8f0; padding-bottom: 0.5rem; } " +
               "table { width: 100%; border-collapse: collapse; margin-top: 1rem; " +
               "box-shadow: 0 2px 4px rgba(0, 0, 0, 0.05); } " +
               "thead { background: linear-gradient(135deg, #667eea 0%, #764ba2 100%); color: white; } " +
               "th { padding: 1rem; text-align: left; font-weight: 600; text-transform: uppercase; " +
               "font-size: 0.85rem; } " +
               "td { padding: 1rem; border-bottom: 1px solid #e2e8f0; } " +
               "tbody tr { background: white; transition: background 0.2s; } " +
               "tbody tr:nth-child(even) { background: #f7fafc; } " +
               "tbody tr:hover { background: #edf2f7; } " +
               "tbody tr.status-ok { border-left: 4px solid #48bb78; } " +
               "tbody tr.status-fail { border-left: 4px solid #f56565; } " +
               "footer { background: #f7fafc; border-top: 1px solid #e2e8f0; padding: 1.5rem; " +
               "text-align: center; color: #718096; font-size: 0.9rem; }";
    }
}

