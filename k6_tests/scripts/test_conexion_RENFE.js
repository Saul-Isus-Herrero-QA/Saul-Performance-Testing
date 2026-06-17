// Se importan las librerías de k6.
import http from 'k6/http';
import { check } from 'k6';

export default function () {
  // Hace una request GET a la web de RENFE
  const response = http.get('https://www.renfe.com');

  // Verificación de resultados
  check(response, {
    'status es 200': (r) => r.status === 200,
    'la página tardó menos de 2 segundos': (r) => r.timings.duration < 2000,
  });
}

// Generación automática de reporte HTML y JSON.
export function handleSummary(data) {
  return {
    "results.html": htmlReport(data),   // HTML
    "results.json": JSON.stringify(data, null, 2), // JSON
  };
}