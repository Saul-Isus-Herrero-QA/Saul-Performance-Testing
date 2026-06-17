# RENFE Performance Testing con k6

## Descripción
Proyecto de tests de performance para la web oficial de RENFE usando k6.

## Estructura de carpetas
```
k6_tests/
├── scripts/          # Scripts de k6 individuales
├── tests/            # Tests agrupados por módulos
├── package.json      # Configuración del proyecto
```

## Instalación
1. Instalar Node.js
2. Instalar k6: npm install -g k6
3. Ejecutar tests: k6 run [archivo]

## Thresholds (umbrales)
En los scripts de k6 se defino `thresholds` (umbrales) dentro de `options`.
Estos umbrales hacen que el test devuelva un estado de fallo si no se cumplen
las condiciones especificadas. Ejemplos comunes:

Si un threshold no se cumple, la ejecución sale con código distinto de 0, Y esto
es útil para pipelines CI/CD.

## Reporting formatos

1) HTML (archivo local): genera un informe visual en HTML para abrir en el navegador.

k6 run scripts/2_test_de_carga.js --out html=results.html


2) JSON : exporta todas las métricas en JSON para procesamiento posterior.

k6 run scripts/2_test_de_carga.js --out json=results.json


## Métricas personalizadas (Custom Metrics)
- **Counter**: cuenta eventos (siempre sube, nunca baja).
- **Gauge**: mide un valor instantáneo (puede subir o bajar).
- **Trend**: registra series temporales de valores.
- **Rate**: calcula porcentaje de eventos.

import { Counter } from 'k6';
const peticionesExitosas = new Counter('peticiones_exitosas');
peticionesExitosas.add(1);  // Incrementar el contador en 1

En el reporte verás la métrica personalizada con su valor final.

## Scripts
1. `tets_conexion_RENFE.js` - Test para comprobar la conexión.
2. `test_carga.js` - Test con incremento/decremento gradual, umbrales/thresholds y stages/escenarios.
3. `test_metricas_personalizadas.js` - Test con Counter personalizado.
