import { PageResponse } from "@/lib/types";

export function getTotalElements<T>(data: PageResponse<T> | null | undefined): number {
    if (!data) return 0;
    if (typeof data.page?.totalElements === "number") return data.page.totalElements;
    if (typeof data.totalElements === "number") return data.totalElements;
    return data.content?.length || 0;
}
