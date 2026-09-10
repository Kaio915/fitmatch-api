from fpdf import FPDF
from pathlib import Path

source = Path('REQUISITOS_Funcionais_Nao_Funcionais.md')
output = Path('REQUISITOS_Funcionais_Nao_Funcionais.pdf')

if not source.exists():
    raise FileNotFoundError(f'Markdown source {source} was not found')

text = source.read_text(encoding='utf-8')

pdf = FPDF()
pdf.set_auto_page_break(True, margin=15)
pdf.add_page()

# Use Arial as default font.
pdf.set_font('Arial', '', 12)
line_height = 6

for raw_line in text.splitlines():
    line = raw_line.rstrip()
    if line.startswith('# '):
        pdf.set_font('Arial', 'B', 16)
        pdf.cell(0, 10, line[2:].strip(), ln=True)
        pdf.set_font('Arial', '', 12)
        continue
    if line.startswith('## '):
        pdf.set_font('Arial', 'B', 14)
        pdf.cell(0, 10, line[3:].strip(), ln=True)
        pdf.set_font('Arial', '', 12)
        continue
    if line.startswith('|') and '|' in line[1:]:
        parts = [p.strip() for p in line.split('|')[1:-1]]
        pdf.set_font('Arial', '', 10)
        pdf.multi_cell(0, line_height, ' | '.join(parts))
        continue
    if not line:
        pdf.ln(4)
        continue
    pdf.set_font('Arial', '', 12)
    pdf.multi_cell(0, line_height, line)

pdf.output(str(output))
print(f'PDF gerado em: {output.resolve()}')
