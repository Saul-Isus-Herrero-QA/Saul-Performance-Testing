# 🚀 Guía Rápida - TestNG K6 Performance Testing

## Inicio Rápido

```powershell
# 1. Ir al directorio del proyecto
cd C:\Users\sisus_amaris.com\Documents\Saul-Performance-Testing

# 2. Ejecutar los tests (ejecuta todos los scripts k6 en paralelo)
mvn test

# 3. Ver el reporte HTML
start results.html
```

## 📊 ¿Qué se genera?

Una sola ejecución de `mvn test` generará:

### Archivos principales:
- **`results.html`** → Reporte HTML consolidado (abre en navegador)
- **`results/results.json`** → Resumen JSON con métrica agregadas de todos los scripts
- **`results/*.json`** → Archivos JSON individuales de cada script (formato NDJSON de k6)

### Ejemplo de salida:
```
========== Iniciando ejecución de todos los scripts k6 ==========
Scripts encontrados: 3
  - test_carga.js
  - test_conexion_RENFE.js
  - test_metricas_personalizadas.js
Ejecutando: k6 run --out json=results/test_carga.json k6_tests\scripts\test_carga.js
Ejecutando: k6 run --out json=results/test_conexion_RENFE.json k6_tests\scripts\test_conexion_RENFE.js
Ejecutando: k6 run --out json=results/test_metricas_personalizadas.json k6_tests\scripts\test_metricas_personalizadas.js
✓ Script completado: test_conexion_RENFE.js
========== Ejecución completada ==========
Resultados JSON: C:\...\results\results.json
Resultados HTML: C:\...\results.html
```

## 🔧 Características Principales

### ✅ Ejecución Paralela
- Todos los scripts se ejecutan **simultáneamente** (no secuencial)
- Tiempo total ≈ tiempo del script más lento (no suma de tiempos)

### ✅ Consolidación Automática
- Se agregan resultados de todos los scripts
- Total de requests, fallos, checks y fallos

### ✅ Reporte HTML Profesional
- Interfaz moderna y responsiva
- Tablas con resultados de cada script
- Métricas resumidas en tarjetas de color

### ✅ Gestión de Errores
- Si un script falla, los otros siguen ejecutándose
- El reporte se genera de todas formas con los datos disponibles

## 📝 Estructura de Archivos

```
Saul-Performance-Testing/
├── pom.xml                              # Configuración Maven (dependencias)
├── testng.xml                           # Configuración TestNG
├── README_TESTNG.md                     # Documentación completa
├── GUIA_RAPIDA.md                       # Este archivo
├── results.html                         # ✨ Reporte HTML final
├── src/main/java/com/saul/performance/
│   └── K6PerformanceTest.java           # Clase principal de TestNG
├── k6_tests/
│   ├── package.json
│   └── scripts/
│       ├── test_carga.js                # Script k6
│       ├── test_conexion_RENFE.js       # Script k6
│       └── test_metricas_personalizadas.js  # Script k6
└── results/                             # Directorio de salida (se crea automáticamente)
    ├── results.json                     # JSON consolidado
    ├── test_carga.json                  # NDJSON individual
    ├── test_conexion_RENFE.json         # NDJSON individual
    └── test_metricas_personalizadas.json  # NDJSON individual
```

## 🛠️ Comandos Útiles

```powershell
# Limpiar y ejecutar todo desde cero
mvn clean test

# Compilar sin ejecutar tests
mvn clean install

# Ejecutar con salida más verbose
mvn clean test -X

# Ver estructura del proyecto
tree /F

# Abrir reporte HTML
Invoke-Item results.html
```

## ⚙️ Personalización

### Cambiar directorio de resultados
En `K6PerformanceTest.java`, línea 29:
```java
private static final String RESULTS_DIR = "mis_resultados";
```

### Cambiar timeout de ejecución
En `K6PerformanceTest.java`, línea 91:
```java
String jsonOutput = entry.getValue().get(10, TimeUnit.MINUTES);  // 10 minutos
```

### Incluir más scripts
Simplemente agrega archivos `.js` a `k6_tests/scripts/`

## 🐛 Troubleshooting

### "k6 command not found"
```powershell
# Verifica instalación
k6 version

# Si no define, instala desde https://k6.io/docs/getting-started/installation/
```

### "No se encontraron scripts .js"
```powershell
# Verifica que existen los scripts
dir k6_tests\scripts\*.js
```

### "Port already in use"
Los scripts k6 pueden estar usando un puerto. Espera o cambia el puerto en los scripts.

### Timeout en test_carga.js
Si `test_carga.js` tarda más de 5 minutos, aumenta el timeout (ver Personalización).

## 📚 Documentación Completa

Ver `README_TESTNG.md` para documentación más detallada.

---

**Última actualización:** Junio 2026
**Versión:** 1.0

