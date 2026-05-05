import { useState, useRef, useCallback } from 'react';
import { Upload, FileText, Send, CheckCircle2, Loader2, Download, X, FileUp } from 'lucide-react';
import { motion, AnimatePresence } from 'motion/react';
import ReactMarkdown from 'react-markdown';
import { cn } from '@/src/lib/utils';

interface ProcessResult {
  file: string;
  content: string;
}

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

  return (
    <div className="min-h-screen bg-[#F9FAFB] flex flex-col items-center py-12 px-4 sm:px-6">
      <motion.header 
        initial={{ opacity: 0, y: -20 }}
        animate={{ opacity: 1, y: 0 }}
        className="w-full max-w-3xl mb-12 text-center"
      >
        <h1 className="text-3xl font-light tracking-tight text-slate-900 mb-2">
          MedScan <span className="font-medium text-blue-600">TCC</span>
        </h1>
        <p className="text-slate-500 font-light">
          Processamento inteligente de laudos médicos
        </p>
      </motion.header>

      <main className="w-full max-w-3xl space-y-6">
        <AnimatePresence mode="wait">
          {!result ? (
            <motion.div
              key="input-section"
              initial={{ opacity: 0, scale: 0.98 }}
              animate={{ opacity: 1, scale: 1 }}
              exit={{ opacity: 0, scale: 0.98 }}
              className="bg-white rounded-2xl shadow-sm border border-slate-200 overflow-hidden"
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
              <div className="bg-white rounded-2xl shadow-sm border border-slate-200 p-8">
                <div className="flex items-center justify-between mb-8 pb-4 border-b border-slate-100">
                  <div className="flex items-center gap-3">
                    <div className="w-10 h-10 bg-blue-50 rounded-full flex items-center justify-center">
                      <CheckCircle2 className="w-5 h-5 text-blue-600" />
                    </div>
                    <div>
                      <h2 className="text-lg font-medium text-slate-900">Análise Concluída</h2>
                      <p className="text-sm text-slate-500 font-light">Resultados processados pelo sistema</p>
                    </div>
                  </div>
                  <button 
                    onClick={reset}
                    className="text-sm text-slate-400 hover:text-slate-600 transition-colors"
                  >
                    Nova análise
                  </button>
                </div>

                <div className="markdown-body prose prose-slate max-w-none">
                  <ReactMarkdown>{result.content}</ReactMarkdown>
                </div>

                <div className="mt-10 pt-6 border-t border-slate-100 flex flex-col sm:flex-row items-center justify-between gap-4">
                  <div className="flex items-center gap-2 text-sm text-slate-500">
                    <FileText className="w-4 h-4" />
                    <span>Arquivo gerado disponível para download</span>
                  </div>
                  <button
                    onClick={downloadResultFile}
                    className="flex items-center gap-2 bg-blue-600 text-white px-6 py-3 rounded-xl hover:bg-blue-700 transition-all shadow-md shadow-blue-200 active:scale-95"
                  >
                    <Download className="w-4 h-4" />
                    Baixar Laudo PDF
                  </button>
                </div>
              </div>
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
