// Se Importan la librerías de k6.
import http from 'k6/http';
import { check } from 'k6';

//Variables : creación de diferentes escenarios para hacer una subida progresiva (ramp-up) de usuarios virtuales (VUs) y luego una bajada progresiva (ramp-down).
export const options = {
  // stages/escenarios.
  stages: [
    { duration: '10s', target: 2 },   // 1) Se sube a 2 usuarios en 10s.
    { duration: '15s', target: 5 },   // 2) Luego se sube a 5 usuarios en 15s.
    { duration: '20s', target: 10 },  // 3) Cómo pico máximo llego a 10 usuarios en 20s.
    { duration: '30s', target: 10 },  // 4) Mantengo 10 usuarios durante 30s.
    { duration: '10s', target: 0 },   // 5) Finalmente, bajo a 0 usuarios en 10s (ramp-down)
  ],

  // Thresholds (umbrales): son reglas que si se cumplen el test falla porque supera el umbral.
  thresholds: {
    // Tiempo de respuesta: el 95% de las peticiones debe ser menor a 2000 ms.
    // Formato: 'metric': ['condition'] -> p(95) < 2000 (p95 < 2s)
    'http_req_duration': ['p(95) < 2000'],

    // Checks: la tasa de checks exitosos debe ser al menos 99%.
    'checks': ['rate>0.99'],

    // Fallos HTTP: la tasa de requests fallidas debe ser menos del 1%.
    'http_req_failed': ['rate<0.01'],
  },
};

// Ejecuto k6.
export default function () {
  // Se hace GET a la página web de RENFE.
  const response = http.get('https://www.renfe.com');

  // Verifico que la petición da un resultado correcto.
  check(response, {
    'status es 200': (r) => r.status === 200, // verifico el status de la respuesta que sea 200 (Correcto).
    'la página tardó menos de 2 segundos': (r) => r.timings.duration < 2000, // verifico que no hay timeout al cargar la web.
  });
}

// Generación automática de reporte HTML y JSON.
export function handleSummary(data) {
  return {
    "results.html": htmlReport(data),   // HTML
    "results.json": JSON.stringify(data, null, 2), // JSON
  };
}