# Saul Performance Testing - K6 Consolidado

Este proyecto ejecuta todos los scripts de performance testing de k6 ubicados en `k6_tests/scripts/` **en paralelo** y genera un reporte HTML consolidado con todos los resultados.

## Requisitos Previos

- **Java 11+** instalado (verificar con `java -version`)
- **Maven 3.6+** instalado (verificar con `mvn -version`)
- **k6** instalado (verificar con `k6 version`)
- Los scripts k6 en `k6_tests/scripts/` deben existir

## Estructura del Proyecto

```
Saul-Performance-Testing/
├── pom.xml                           # Configuración Maven con dependencias
├── testng.xml                        # Configuración TestNG
├── src/main/java/com/saul/performance/
│   └── K6PerformanceTest.java        # Clase principal que ejecuta los tests
├── k6_tests/
│   └── scripts/
│       ├── test_carga.js
│       ├── test_conexion_RENFE.js
│       └── test_metricas_personalizadas.js
└── results/                          # Directorio de salida (se crea automáticamente)
    └── *.json                        # Resultados JSON de cada script
```

## Instalación y Ejecución

### 1. Descargar e instalar dependencias

```powershell
cd C:\Users\sisus_amaris.com\Documents\Saul-Performance-Testing
mvn clean install
```

### 2. Ejecutar los tests

```powershell
mvn test
```

## Resultados

Después de ejecutar los tests, encontrarás:

- **`results.html`** - Reporte HTML consolidado (abierto en navegador)
- **`results/results.json`** - JSON consolidado con métricas de todos los scripts
- **`results/test_carga.json`** - Resultados individuales de cada script
- **`results/test_conexion_RENFE.json`**
- **`results/test_metricas_personalizadas.json`**

### Abrir el Reporte HTML

```powershell
# Windows
start results.html

# O desde PowerShell
Invoke-Item results.html
```

## Cómo Funciona

1. **Descubrimiento**: La clase `K6PerformanceTest` busca todos los archivos `.js` en `k6_tests/scripts/`
2. **Ejecución Paralela**: Cada script se ejecuta en paralelo usando un `ExecutorService`
3. **Recolección**: Los resultados JSON de cada ejecución se guardan en `results/`
4. **Consolidación**: Se agrega toda la información en un JSON consolidado
5. **Reporte HTML**: Se genera un reporte HTML con:
   - Total de scripts ejecutados
   - Total de requests y fallos
   - Total de checks y fallos
   - Tabla detallada con resultados por script

## Configuración Avanzada

### Timeout de Ejecución

En `K6PerformanceTest.java`, línea 91, modificar el timeout (actualmente 5 minutos):

```java
String jsonOutput = entry.getValue().get(5, TimeUnit.MINUTES);
```

### Número de Threads Paralelos

En línea 74, modificar el pool de threads:

```java
ExecutorService executor = Executors.newFixedThreadPool(scriptFiles.size());
```

### Directorio de Scripts

En línea 29, cambiar la ruta si los scripts están en otro lugar:

```java
private static final String K6_SCRIPTS_DIR = "k6_tests/scripts";
```

## Troubleshooting

### Error: "k6 command not found"

Asegúrate de que k6 está instalado y en el PATH:

```powershell
k6 version
```

Si no funciona, instala k6 desde: https://k6.io/docs/getting-started/installation/

### Error: "No se encontraron scripts .js"

Verifica que existen archivos `.js` en `k6_tests/scripts/`:

```powershell
dir k6_tests\scripts\*.js
```

### Error: "Cannot find symbol 'K6PerformanceTest'"

Ejecuta:

```powershell
mvn clean compile
```

## Dependencias Utilizadas

- **TestNG 7.8.1**: Framework de testing
- **Gson 2.10.1**: Parseo y generación de JSON
- **Apache Commons Exec 1.3**: Ejecución de procesos del sistema
- **Apache Commons IO 2.13.0**: Utilidades de I/O

## Notas

- Los scripts se ejecutan con la opción `--out json=` para generar salida JSON
- El reporte HTML se sobrescribe en cada ejecución
- Los JSON individuales se guardan en `results/` con el nombre del script
- La consolidación suma totales de requests, failed requests, checks y failed checks

## Licencia

ISC

---

**Última actualización**: Junio 2026

