import React from 'react';
import { MediaPlayer, MediaProvider } from '@vidstack/react';
import { DefaultVideoLayout, defaultLayoutIcons } from '@vidstack/react/player/layouts/default';
import '@vidstack/react/player/styles/default/theme.css';
import '@vidstack/react/player/styles/default/layouts/video.css';
import { SP_CHINESE } from '../../utils/playerI18n';

function LazyVideoPlayer({ activeUrl, videoTitle, onEnded }) {
    return (
        <MediaPlayer
            key={activeUrl}
            title={videoTitle}
            src={activeUrl}
            load="eager"
            autoplay
            streamType="on-demand"
            lang="zh-CN"
            onEnded={onEnded}
            style={{ width: '100%', height: '100%' }}
        >
            <MediaProvider />
            <DefaultVideoLayout
                icons={defaultLayoutIcons}
                translations={SP_CHINESE}
            />
        </MediaPlayer>
    );
}

export default LazyVideoPlayer;
