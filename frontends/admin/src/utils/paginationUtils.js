
export const getTotalElements = (data) => {
    if (!data) return 0;
    if (data.page && typeof data.page.totalElements === 'number') {
        return data.page.totalElements;
    }
    if (typeof data.totalElements === 'number') {
        return data.totalElements;
    }
    return 0;
};