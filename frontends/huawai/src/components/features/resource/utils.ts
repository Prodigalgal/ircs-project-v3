
export const cleanEpisodeTitle = (rawTitle: string): string => {
    let title = rawTitle.replace(/(\[.*?\]|\(.*?\)|【.*?】)/g, "").trim();

    if (/^\d+$/.test(title)) {
        return title.padStart(2, '0');
    }

    title = title.replace(/(HD|BD|中字|国语|高清|修正|完整版)/gi, "").trim();

    if (!title) {
        return rawTitle.replace(/(\[.*?\])/g, "").substring(0, 8);
    }

    return title;
};