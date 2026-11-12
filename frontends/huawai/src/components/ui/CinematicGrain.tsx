const GRAIN_DATA_URI = "data:image/svg+xml,%3Csvg xmlns='http://www.w3.org/2000/svg' width='120' height='120' viewBox='0 0 120 120'%3E%3Cfilter id='n'%3E%3CfeTurbulence type='fractalNoise' baseFrequency='.85' numOctaves='3' stitchTiles='stitch'/%3E%3C/filter%3E%3Crect width='120' height='120' filter='url(%23n)' opacity='.45'/%3E%3C/svg%3E";

export function CinematicGrain() {
    return (
        <div
            aria-hidden="true"
            className="pointer-events-none fixed inset-0 z-[9999] opacity-[0.025]"
            style={{
                backgroundImage: `url("${GRAIN_DATA_URI}")`,
                backgroundRepeat: "repeat",
                backgroundSize: "120px 120px",
                mixBlendMode: "screen",
            }}
        />
    );
}
