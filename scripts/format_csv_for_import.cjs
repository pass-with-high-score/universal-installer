const fs = require('fs');

const inputPath = 'merged_translations.csv';
const outputPath = 'formatted_translations.csv';

const content = fs.readFileSync(inputPath, 'utf8').split('\n');
const header = content[0].split(',');

// Map Key to name, Language to locale, Translation to translated_value
const keyIdx = header.indexOf('Key');
const langIdx = header.indexOf('Language');
const transIdx = header.indexOf('Translation');

const formattedLines = ['name,locale,translated_value'];

for (let i = 1; i < content.length; i++) {
    const line = content[i].trim();
    if (!line) continue;
    
    // Simple CSV parser for lines with quotes
    const cols = [];
    let curr = '';
    let inQuotes = false;
    for (let j = 0; j < line.length; j++) {
        if (line[j] === '"') {
            inQuotes = !inQuotes;
        } else if (line[j] === ',' && !inQuotes) {
            cols.push(curr);
            curr = '';
        } else {
            curr += line[j];
        }
    }
    cols.push(curr);

    if (cols.length >= 4) {
        const name = `"${cols[keyIdx]}"`;
        const locale = `"${cols[langIdx]}"`;
        const translated_value = `"${cols[transIdx]}"`;
        formattedLines.push(`${name},${locale},${translated_value}`);
    }
}

fs.writeFileSync(outputPath, formattedLines.join('\n'));
console.log('Formatted CSV saved to ' + outputPath);
