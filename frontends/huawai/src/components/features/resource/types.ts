import { MovieDetail } from "@/lib/types";
import React from "react";

export interface ResourcePanelProps {
    movie: MovieDetail;
}

export interface TabProps {
    isActive: boolean;
    onClick: () => void;
    label: string;
    icon: React.ReactNode;
}