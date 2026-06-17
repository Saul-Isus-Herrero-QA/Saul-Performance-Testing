// Se Importan las librerías de k6.
import http from 'k6/http';
import { check, Counter } from 'k6';
import { htmlReport } from "./reporter.js";

// Creo una métrica personalizada de tipo Counter/Contador incremental.
// Se usa para contar eventos: peticiones exitosas, errores, etc.
const contadorPeticionesExitosas = new Counter('peticiones_exitosas');

export const options = {
  stages: [
    { duration: '10s', target: 2 },   // sube a 2 usuarios en 10s.
    { duration: '15s', target: 5 },   // sube a 5 usuarios en 15s.
    { duration: '20s', target: 10 },  // sube a 10 usuarios en 20s.
    { duration: '30s', target: 10 },  // mantiene 10 usuarios durante 30s.
    { duration: '10s', target: 0 },   // baja a 0 usuarios en 10s.
  ],

  thresholds: {
    'http_req_duration': ['p(95) < 2000'],
    'checks': ['rate>0.99'],
    'http_req_failed': ['rate<0.01'],
  },
};

export default function () {
  // Se hace GET a la página web de RENFE.
  const response = http.get('https://www.renfe.com');

  // Verifico que la petición da un resultado correcto.
  const resultadoCheck = check(response, {
    'status es 200': (r) => r.status === 200,
    'la página tardó menos de 2 segundos': (r) => r.timings.duration < 2000,
  });

  // Incremento la métrica personalizada
  // Si los 2 checks dieron PASSED, incrementamos el contador de peticiones exitosas.
  if (resultadoCheck) {
    contadorPeticionesExitosas.add(1);
      }
}

// Generación automática de reporte HTML y JSON.

export function handleSummary(data) {
  return {
    'results.html': htmlReport(data),            // HTML
    'results.json': JSON.stringify(data, null, 2), // JSON

  };
}
