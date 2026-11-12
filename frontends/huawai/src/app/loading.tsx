import Image from "next/image";

export default function Loading() {
    return (
        <div className="fixed inset-0 z-50 bg-background flex items-center justify-center">
            <div className="relative flex flex-col items-center gap-4">
                <div className="relative w-16 h-16 flex items-center justify-center">
                    <div className="absolute inset-0 bg-primary/20 rounded-xl blur-lg animate-pulse" />

                    <div className="relative z-10 w-16 h-16 animate-[bounce_2s_infinite]">
                        <Image
                            src="/logo.svg"
                            alt="Huawai Loading"
                            fill
                            className="[.dark_&]:invert drop-shadow-xl object-contain"
                            priority
                        />
                    </div>
                </div>
                <div className="w-32 h-1 bg-secondary rounded-full overflow-hidden mt-4">
                    <div className="w-full h-full bg-muted-foreground/50 origin-left animate-[shimmer_1.5s_infinite]" />
                </div>
                <p className="text-xs text-muted-foreground font-mono tracking-widest uppercase animate-pulse">
                    Loading...
                </p>
            </div>
        </div>
    );
}