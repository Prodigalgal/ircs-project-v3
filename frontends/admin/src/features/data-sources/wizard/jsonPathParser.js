// File: /ircs-ui/src/features/data-sources/wizard/jsonPathParser.js

/**
 * Recursively traverses a JSON object and generates an array of JSONPath strings.
 * @param {object} obj The JSON object to parse.
 * @param {string} path The current path prefix.
 * @param {string[]} acc The accumulator array for the paths.
 */
const traverse = (obj, path, acc) => {
    if (obj === null || typeof obj !== 'object') {
        return;
    }

    if (Array.isArray(obj)) {
        obj.forEach((item, index) => {
            traverse(item, `${path}[${index}]`, acc);
        });
    } else {
        Object.keys(obj).forEach(key => {
            const newPath = path ? `${path}.${key}` : `$.${key}`;
            acc.push(newPath);
            traverse(obj[key], newPath, acc);
        });
    }
};

/**
 * Parses a JSON object and returns a flat array of all possible JSONPath strings.
 * @param {object} json The JSON object.
 * @returns {string[]} An array of JSONPath strings.
 */
export const parseJsonToPaths = (json) => {
    if (!json || typeof json !== 'object') {
        return [];
    }
    const paths = ['$'];
    traverse(json, '$', paths);
    return [...new Set(paths)]; // Return unique paths
};