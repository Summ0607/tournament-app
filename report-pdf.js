const fs = require('fs');
const PDFDocument = require('pdfkit');

function pad2(value) {
  return String(value).padStart(2, '0');
}

function buildTimestampForFile(date = new Date()) {
  return [
    date.getFullYear(),
    '-',
    pad2(date.getMonth() + 1),
    '-',
    pad2(date.getDate()),
    '_',
    pad2(date.getHours()),
    '-',
    pad2(date.getMinutes()),
    '-',
    pad2(date.getSeconds())
  ].join('');
}

function writeTextPdf(outputPath, title, lines) {
  return new Promise((resolve, reject) => {
    const doc = new PDFDocument({ size: 'A4', layout: 'landscape', margin: 42 });
    const stream = fs.createWriteStream(outputPath);
    const pageWidth = doc.page.width - doc.page.margins.left - doc.page.margins.right;
    const pageBottom = doc.page.height - doc.page.margins.bottom;
    const lineHeight = 12;

    function writeHeading() {
      doc.font('Helvetica-Bold').fontSize(16).text(title);
      doc.moveDown(0.5);
      doc.font('Courier').fontSize(9);
    }

    function ensureSpace(extraHeight) {
      if (doc.y + extraHeight > pageBottom) {
        doc.addPage();
        writeHeading();
      }
    }

    stream.on('finish', resolve);
    stream.on('error', reject);
    doc.on('error', reject);
    doc.pipe(stream);

    writeHeading();
    lines.forEach((line) => {
      const value = String(line);
      ensureSpace(lineHeight);
      doc.text(value, { width: pageWidth, lineBreak: false });
    });
    doc.end();
  });
}

module.exports = {
  buildTimestampForFile,
  writeTextPdf
};
