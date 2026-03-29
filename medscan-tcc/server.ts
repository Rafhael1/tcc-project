import express from "express";
import path from "path";
import { createServer as createViteServer } from "vite";

async function startServer() {
  const app = express();
  const PORT = 3000;

  app.use(express.json({ limit: '50mb' }));

  // Mock API for medical report processing
  app.post("/api/process-report", (req, res) => {
    const { text, fileName } = req.body;
    
    // Simulate processing delay
    setTimeout(() => {
      res.json({
        file: "JVBERi0xLjQKJ... (base64 mock PDF content)", 
        content: `## Relatório de Análise Médica\n\n**Paciente:** Simulado\n**Data:** ${new Date().toLocaleDateString()}\n\n### Observações Principais\nO laudo enviado (${fileName || 'Texto direto'}) foi processado com sucesso. \n\n1. **Análise de Texto:** O conteúdo apresenta indicadores normais.\n2. **Recomendações:** Consultar especialista para validação.\n\nEste é um exemplo de retorno do backend para o seu TCC.`
      });
    }, 2000);
  });

  if (process.env.NODE_ENV !== "production") {
    const vite = await createViteServer({
      server: { middlewareMode: true },
      appType: "spa",
    });
    app.use(vite.middlewares);
  } else {
    const distPath = path.join(process.cwd(), 'dist');
    app.use(express.static(distPath));
    app.get('*', (req, res) => {
      res.sendFile(path.join(distPath, 'index.html'));
    });
  }

  app.listen(PORT, "0.0.0.0", () => {
    console.log(`Server running on http://localhost:${PORT}`);
  });
}

startServer();
