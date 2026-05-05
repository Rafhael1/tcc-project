import { useState, useRef, useCallback } from 'react';
import { AlertTriangle, CheckCircle2, ClipboardList, Download, FileUp, Loader2, RotateCcw, Send, ShieldCheck, Stethoscope, X } from 'lucide-react';
import { motion, AnimatePresence } from 'motion/react';
import ReactMarkdown from 'react-markdown';
import { cn } from '@/src/lib/utils';

interface ProcessResult {
  file: string;
  content: string;
}

interface ReportContent {
  summary: string;
  details: string;
  recommendation: string;
  legal: string;
}

interface ParsedResult {
  report: ReportContent | null;
  fallback: string;
}

const stripJsonFence = (content: string) => (
  content
    .trim()
    .replace(/^```(?:json)?\s*/i, '')
    .replace(/\s*```$/i, '')
);

const cleanReportText = (content: string) => (
  content
    .replace(/^#{1,6}\s+/gm, '')
    .replace(/(^|\s)[*_]{1,3}([^*_]+)[*_]{1,3}(?=\s|$|[.,;:!?])/g, '$1$2')
    .replace(/^\s*[-*]\s+/gm, '')
    .replace(/^\s*>+\s?/gm, '')
    .replace(/`{1,3}/g, '')
    .trim()
);

const stringifyReportValue = (value: unknown) => {
  if (typeof value === 'string') return cleanReportText(value);
  if (Array.isArray(value)) return value.map(stringifyReportValue).filter(Boolean).join('\n');
  if (value && typeof value === 'object') {
    return Object.entries(value)
      .map(([key, entry]) => `${key}: ${stringifyReportValue(entry)}`)
      .filter(Boolean)
      .join('\n');
  }
  return value == null ? '' : cleanReportText(String(value));
};

const parseResultContent = (content: string): ParsedResult => {
  const jsonCandidate = stripJsonFence(content || '');
  const fallback = cleanReportText(jsonCandidate) || 'Nenhum conteúdo foi gerado.';

  try {
    const parsed = JSON.parse(jsonCandidate) as Record<string, unknown>;
    const report = {
      summary: stringifyReportValue(parsed.summary) || 'Laudo processado com sucesso.',
      details: stringifyReportValue(parsed.details) || 'Os detalhes do laudo foram processados pelo sistema.',
      recommendation: stringifyReportValue(parsed.recommendation) || 'Consulte um profissional de saúde para interpretar os resultados.',
      legal: stringifyReportValue(parsed.legal) || 'Este conteúdo não substitui avaliação médica profissional.',
    };

    return { report, fallback };
  } catch {
    return { report: null, fallback };
  }
};

export default function App() {
  const [file, setFile] = useState<File | null>(null);
  const [isProcessing, setIsProcessing] = useState(false);
  const [result, setResult] = useState<ProcessResult | null>(null);
  const [error, setError] = useState('');
  const [isDragging, setIsDragging] = useState(false);
  const fileInputRef = useRef<HTMLInputElement>(null);

  const setSelectedFile = (selectedFile: File) => {
    if (selectedFile.type !== 'application/pdf') {
      setError('Envie um arquivo PDF para processar o laudo.');
      setFile(null);
      setResult(null);
      return;
    }

    setFile(selectedFile);
    setError('');
    setResult(null);
  };

  const handleFileChange = (e: React.ChangeEvent<HTMLInputElement>) => {
    if (e.target.files && e.target.files[0]) {
      setSelectedFile(e.target.files[0]);
    }
  };

  const onDragOver = useCallback((e: React.DragEvent) => {
    e.preventDefault();
    setIsDragging(true);
  }, []);

  const onDragLeave = useCallback((e: React.DragEvent) => {
    e.preventDefault();
    setIsDragging(false);
  }, []);

  const onDrop = useCallback((e: React.DragEvent) => {
    e.preventDefault();
    setIsDragging(false);
    if (e.dataTransfer.files && e.dataTransfer.files[0]) {
      setSelectedFile(e.dataTransfer.files[0]);
    }
  }, []);

  const handleSubmit = async () => {
    if (!file) {
      setError('Selecione um PDF antes de analisar.');
      return;
    }

    setIsProcessing(true);
    setResult(null);
    setError('');

    try {
      const formData = new FormData();
      formData.append('file', file);

      const response = await fetch('/api/generate-report', {
        method: 'POST',
        body: formData,
      });

      const data = await response.json();
      if (!response.ok) {
        throw new Error(data?.message || 'Não foi possível processar o laudo.');
      }

      setResult(data);
    } catch (error) {
      console.error('Error processing report:', error);
      setError(error instanceof Error ? error.message : 'Não foi possível processar o laudo.');
    } finally {
      setIsProcessing(false);
    }
  };

  const downloadResultFile = () => {
    if (!result?.file) return;
    const link = document.createElement('a');
    link.href = `data:application/pdf;base64,${result.file}`;
    link.download = 'laudo_processado.pdf';
    link.click();
  };

  const reset = () => {
    setFile(null);
    setResult(null);
    setError('');
  };

  const parsedResult = result ? parseResultContent(result.content) : null;
  const reportSections = parsedResult?.report ? [
    {
      title: 'Resumo',
      content: parsedResult.report.summary,
      icon: Stethoscope,
      className: 'border-blue-100 bg-blue-50/60 text-blue-700',
    },
    {
      title: 'Detalhes explicados',
      content: parsedResult.report.details,
      icon: ClipboardList,
      className: 'border-slate-200 bg-white text-slate-700',
    },
    {
      title: 'Próximos passos',
      content: parsedResult.report.recommendation,
      icon: CheckCircle2,
      className: 'border-emerald-100 bg-emerald-50/70 text-emerald-700',
    },
    {
      title: 'Aviso legal',
      content: parsedResult.report.legal,
      icon: ShieldCheck,
      className: 'border-amber-100 bg-amber-50/70 text-amber-700',
    },
  ] : [];

  return (
    <div className="min-h-screen bg-[#F9FAFB] flex flex-col items-center py-12 px-4 sm:px-6">
      <motion.header 
        initial={{ opacity: 0, y: -20 }}
        animate={{ opacity: 1, y: 0 }}
        className="w-full max-w-5xl mb-12 text-center"
      >
        <h1 className="text-3xl font-light tracking-tight text-slate-900 mb-2">
          MedScan <span className="font-medium text-blue-600">TCC</span>
        </h1>
        <p className="text-slate-500 font-light">
          Processamento inteligente de laudos médicos
        </p>
      </motion.header>

      <main className="w-full max-w-5xl space-y-6">
        <AnimatePresence mode="wait">
          {!result ? (
            <motion.div
              key="input-section"
              initial={{ opacity: 0, scale: 0.98 }}
              animate={{ opacity: 1, scale: 1 }}
              exit={{ opacity: 0, scale: 0.98 }}
              className="max-w-3xl mx-auto bg-white rounded-2xl shadow-sm border border-slate-200 overflow-hidden"
            >
              <div className="p-8 space-y-8">
                {/* File Upload Area */}
                <div
                  onDragOver={onDragOver}
                  onDragLeave={onDragLeave}
                  onDrop={onDrop}
                  onClick={() => fileInputRef.current?.click()}
                  className={cn(
                    "relative border-2 border-dashed rounded-xl p-10 transition-all cursor-pointer flex flex-col items-center justify-center text-center",
                    isDragging 
                      ? "border-blue-400 bg-blue-50/50" 
                      : "border-slate-200 hover:border-slate-300 hover:bg-slate-50/50",
                    file && "border-green-200 bg-green-50/30"
                  )}
                >
                  <input
                    type="file"
                    ref={fileInputRef}
                    onChange={handleFileChange}
                    className="hidden"
                    accept="application/pdf,.pdf"
                  />
                  
                  {file ? (
                    <div className="flex flex-col items-center">
                      <div className="w-12 h-12 bg-green-100 rounded-full flex items-center justify-center mb-4">
                        <CheckCircle2 className="w-6 h-6 text-green-600" />
                      </div>
                      <p className="text-slate-900 font-medium mb-1">{file.name}</p>
                      <p className="text-slate-500 text-sm">{(file.size / 1024).toFixed(1)} KB</p>
                      <button 
                        onClick={(e) => { e.stopPropagation(); setFile(null); setError(''); }}
                        className="mt-4 text-xs text-slate-400 hover:text-red-500 flex items-center gap-1"
                      >
                        <X className="w-3 h-3" /> Remover arquivo
                      </button>
                    </div>
                  ) : (
                    <>
                      <div className="w-12 h-12 bg-blue-50 rounded-full flex items-center justify-center mb-4">
                        <FileUp className="w-6 h-6 text-blue-500" />
                      </div>
                      <p className="text-slate-900 font-medium mb-1">Arraste seu laudo aqui</p>
                      <p className="text-slate-500 text-sm font-light">Arquivo PDF do laudo médico</p>
                    </>
                  )}
                </div>

                {error && (
                  <div className="rounded-xl border border-red-100 bg-red-50 px-4 py-3 text-sm text-red-700">
                    {error}
                  </div>
                )}

                <button
                  onClick={handleSubmit}
                  disabled={isProcessing || !file}
                  className={cn(
                    "w-full py-4 rounded-xl font-medium transition-all flex items-center justify-center gap-2 shadow-sm",
                    isProcessing 
                      ? "bg-slate-100 text-slate-400 cursor-not-allowed"
                      : "bg-slate-900 text-white hover:bg-slate-800 active:scale-[0.99] disabled:opacity-50 disabled:cursor-not-allowed"
                  )}
                >
                  {isProcessing ? (
                    <>
                      <Loader2 className="w-5 h-5 animate-spin" />
                      Processando Laudo...
                    </>
                  ) : (
                    <>
                      <Send className="w-5 h-5" />
                      Analisar Agora
                    </>
                  )}
                </button>
              </div>
            </motion.div>
          ) : (
            <motion.div
              key="result-section"
              initial={{ opacity: 0, y: 20 }}
              animate={{ opacity: 1, y: 0 }}
              exit={{ opacity: 0, y: -20 }}
              className="space-y-6"
            >
              <div className="rounded-2xl bg-slate-950 text-white shadow-sm overflow-hidden">
                <div className="flex flex-col gap-6 p-6 sm:flex-row sm:items-center sm:justify-between sm:p-8">
                  <div className="flex items-start gap-4">
                    <div className="w-12 h-12 shrink-0 rounded-xl bg-white/10 flex items-center justify-center">
                      <CheckCircle2 className="w-6 h-6 text-emerald-300" />
                    </div>
                    <div className="space-y-1">
                      <h2 className="text-2xl font-medium tracking-normal">Análise concluída</h2>
                      <p className="text-sm text-slate-300">Resultado simplificado e PDF pronto para download</p>
                    </div>
                  </div>
                  <div className="flex flex-col gap-3 sm:flex-row">
                    <button
                      onClick={reset}
                      className="inline-flex items-center justify-center gap-2 rounded-lg border border-white/15 px-4 py-3 text-sm text-slate-100 transition-colors hover:bg-white/10"
                    >
                      <RotateCcw className="w-4 h-4" />
                      Nova análise
                    </button>
                    <button
                      onClick={downloadResultFile}
                      className="inline-flex items-center justify-center gap-2 rounded-lg bg-blue-500 px-4 py-3 text-sm font-medium text-white transition-colors hover:bg-blue-400"
                    >
                      <Download className="w-4 h-4" />
                      Baixar PDF
                    </button>
                  </div>
                </div>
              </div>

              {parsedResult?.report ? (
                <div className="grid gap-4 lg:grid-cols-2">
                  {reportSections.map((section) => {
                    const Icon = section.icon;
                    return (
                      <section
                        key={section.title}
                        className={cn(
                          'rounded-xl border p-6 shadow-sm',
                          ['Resumo', 'Detalhes explicados'].includes(section.title) && 'lg:col-span-2',
                          section.className
                        )}
                      >
                        <div className="mb-4 flex items-center gap-3">
                          <div className="flex h-10 w-10 items-center justify-center rounded-lg bg-white/70">
                            <Icon className="h-5 w-5" />
                          </div>
                          <h3 className="text-base font-semibold text-slate-900">{section.title}</h3>
                        </div>
                        <p className="whitespace-pre-line text-sm leading-7 text-slate-700">
                          {section.content}
                        </p>
                      </section>
                    );
                  })}
                </div>
              ) : (
                <div className="rounded-xl border border-slate-200 bg-white p-6 shadow-sm">
                  <div className="mb-4 flex items-center gap-3 text-amber-700">
                    <div className="flex h-10 w-10 items-center justify-center rounded-lg bg-amber-50">
                      <AlertTriangle className="h-5 w-5" />
                    </div>
                    <h3 className="text-base font-semibold text-slate-900">Resultado gerado</h3>
                  </div>
                  <div className="markdown-body prose prose-slate max-w-none">
                    <ReactMarkdown>{parsedResult?.fallback || result.content}</ReactMarkdown>
                  </div>
                </div>
              )}
            </motion.div>
          )}
        </AnimatePresence>
      </main>

      <footer className="mt-12 text-slate-400 text-xs font-light tracking-widest uppercase">
        © 2026 MedScan Project • TCC Interface
      </footer>
    </div>
  );
}
