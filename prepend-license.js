import fs from 'fs';
import path from 'path';

const LICENSE_TEXT = `/*
 * Pörssiohjain - Energy usage optimization platform
 * Copyright (C) 2026  Martin Kankaanranta / Nitramite Tmi
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as
 * published by the Free Software Foundation, version 3.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with this program. If not, see <https://www.gnu.org/licenses/>.
 */

`;

const TARGET_FOLDER = './src/main/java';
const FILE_EXTENSIONS = ['.java'];

function prependLicense(filePath) {
    const content = fs.readFileSync(filePath, 'utf-8');
    if (content.includes('Pörssiohjain - Energy usage optimization platform')) return;
    fs.writeFileSync(filePath, LICENSE_TEXT + content, 'utf-8');
    console.log('Added license to:', filePath);
}

function walkDir(dir) {
    const files = fs.readdirSync(dir);

    for (const file of files) {
        const fullPath = path.join(dir, file);
        const stat = fs.statSync(fullPath);

        if (stat.isDirectory()) {
            walkDir(fullPath);
        } else if (FILE_EXTENSIONS.includes(path.extname(file))) {
            prependLicense(fullPath);
        }
    }
}

walkDir(TARGET_FOLDER);