export function toDateInputValue(value) {
    if (!value || typeof value !== 'string') {
        return '';
    }
    return value.slice(0, 10);
}

export function toTimeInputValue(value) {
    if (!value || typeof value !== 'string') {
        return '';
    }
    return value.slice(0, 5);
}
