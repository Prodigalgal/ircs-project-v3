// File: ircs-frontend/src/features/data-sources/wizard/steps/Step3_DetailMapping.jsx
import React from 'react';
import {Alert, Button, CircularProgress, Divider, Paper, TextField, Typography} from '@mui/material';
import Grid from '@mui/material/Grid';
import {useDispatch, useSelector} from 'react-redux';
import {setSampleJson, updateFieldMapping, updateFormData} from '../dataSourceWizardSlice';
import {useFetchSampleDataMutation} from '../../dataSourcesApi';
import JsonPathSelector from '../components/JsonPathSelector';
import {toSourceNetworkPayload} from '../networkOptions';

function Step3_DetailMapping() {
    const dispatch = useDispatch();
    const {formData, sampleJsonResponse, jsonPaths} = useSelector((state) => state.dataSourceWizard);
    const detailMapping = formData.fieldMapping.detail_mapping;

    const [fetchSample, {isLoading, error}] = useFetchSampleDataMutation();

    const handleChange = (event) => {
        dispatch(updateFormData({[event.target.name]: event.target.value}));
    };

    const handleMappingChange = (key, mapType = 'detail_mapping') => (event) => {
        dispatch(updateFieldMapping({mapType, key, value: event.target.value}));
    };

    const handleFetchSample = async () => {
        if (!formData.sampleId) {
            alert("未能从上一步获取到示例ID。请确保在第2步正确映射了“视频主键ID路径”。");
            return;
        }
        const payload = {
            requestType: 'DETAIL',
            baseUrl: formData.baseUrl,
            detailPath: formData.detailPath,
            detailParams: formData.detailParams,
            sampleId: formData.sampleId,
            ...toSourceNetworkPayload(formData),
        };

        try {
            const result = await fetchSample(payload).unwrap();
            dispatch(setSampleJson({data: result, type: 'DETAIL'}));
        } catch {
            // Error handled by middleware
        }
    };

    return (
        <React.Fragment>
            <Grid container spacing={4}>
                <Grid size={{xs: 12, md: 5}}>
                    <Typography variant="h6" gutterBottom>配置详情接口</Typography>
                    <Typography variant="body2" sx={{
                        color: "text.secondary"
                    }}>使用示例
                        ID: <strong>{formData.sampleId || '无'}</strong></Typography>
                    <TextField required id="detailPath" name="detailPath" label="详情接口路径 (Path)" fullWidth
                               variant="outlined" value={formData.detailPath} onChange={handleChange} margin="normal"/>
                    <TextField id="detailParams" name="detailParams" label="接口参数 (JSON)" fullWidth multiline
                               rows={3} variant="outlined" value={formData.detailParams} onChange={handleChange}
                               margin="normal"/>
                    <Button variant="contained" onClick={handleFetchSample} disabled={isLoading || !formData.sampleId}
                            sx={{mt: 1}}>
                        {isLoading ? <CircularProgress size={24}/> : '获取示例详情数据'}
                    </Button>
                    <Paper variant="outlined" sx={{
                        p: 2,
                        mt: 2,
                        minHeight: 250,
                        backgroundColor: '#222',
                        overflow: 'auto',
                        maxHeight: '40vh'
                    }}>
                        <Typography variant="subtitle2" sx={{color: 'text.secondary', mb: 1}}>API 响应预览</Typography>
                        {error && <Alert severity="error">请求失败 (详情见右上角通知)</Alert>}
                        {sampleJsonResponse && <pre style={{
                            color: '#eee',
                            margin: 0,
                            whiteSpace: 'pre-wrap',
                            wordBreak: 'break-all'
                        }}>{JSON.stringify(sampleJsonResponse, null, 2)}</pre>}
                        {!sampleJsonResponse && !isLoading &&
                            <Typography variant="body2" sx={{
                                color: "text.secondary"
                            }}>点击上方按钮以获取数据。</Typography>}
                    </Paper>
                </Grid>

                <Grid size={{xs: 12, md: 7}}>
                    <Typography variant="h6" gutterBottom>详情字段映射</Typography>

                    <JsonPathSelector
                        label="视频主键ID路径 (Source VID)"
                        value={detailMapping.source_vid}
                        onChange={handleMappingChange('source_vid')}
                        jsonPaths={jsonPaths}
                        required
                        helperText="必填。针对非凡API通常为: $.list[0].vod_id"
                    />

                    <JsonPathSelector label="标题路径" value={detailMapping.title}
                                      onChange={handleMappingChange('title')} jsonPaths={jsonPaths} required
                                      helperText="通常为: $.list[0].vod_name"/>

                    <JsonPathSelector label="别名/副标题路径 (Alias Title)" value={detailMapping.aliasTitle}
                                      onChange={handleMappingChange('aliasTitle')} jsonPaths={jsonPaths}
                                      helperText="通常为: $.list[0].vod_sub"/>

                    <Divider sx={{my: 2}}><Typography variant="overline">外部权威 ID
                        (用于精准聚合)</Typography></Divider>
                    <JsonPathSelector label="豆瓣 ID 路径" value={detailMapping.doubanId}
                                      onChange={handleMappingChange('doubanId')} jsonPaths={jsonPaths}/>
                    <JsonPathSelector label="TMDB ID 路径" value={detailMapping.tmdbId}
                                      onChange={handleMappingChange('tmdbId')} jsonPaths={jsonPaths}/>
                    <JsonPathSelector label="IMDb ID 路径" value={detailMapping.imdbId}
                                      onChange={handleMappingChange('imdbId')} jsonPaths={jsonPaths}/>
                    <JsonPathSelector label="Rotten Tomatoes ID 路径" value={detailMapping.rottenTomatoesId}
                                      onChange={handleMappingChange('rottenTomatoesId')} jsonPaths={jsonPaths}/>
                    <Divider sx={{my: 2}}/>

                    <JsonPathSelector label="封面图URL路径" value={detailMapping.coverImageUrl}
                                      onChange={handleMappingChange('coverImageUrl')} jsonPaths={jsonPaths}
                                      helperText="通常为: $.list[0].vod_pic"/>
                    <JsonPathSelector label="简介描述路径" value={detailMapping.description}
                                      onChange={handleMappingChange('description')} jsonPaths={jsonPaths}
                                      helperText="通常为: $.list[0].vod_content"/>
                    <JsonPathSelector label="年份路径" value={detailMapping.year} onChange={handleMappingChange('year')}
                                      jsonPaths={jsonPaths}/>
                    <JsonPathSelector label="地区路径" value={detailMapping.area} onChange={handleMappingChange('area')}
                                      jsonPaths={jsonPaths}/>
                    <JsonPathSelector label="演员路径" value={detailMapping.actors}
                                      onChange={handleMappingChange('actors')} jsonPaths={jsonPaths}
                                      helperText="例如: $.list[0].vod_actor"/>
                    <JsonPathSelector label="导演路径" value={detailMapping.directors}
                                      onChange={handleMappingChange('directors')} jsonPaths={jsonPaths}
                                      helperText="例如: $.list[0].vod_director"/>
                    <JsonPathSelector label="题材标签路径 (Genres)" value={detailMapping.genres}
                                      onChange={handleMappingChange('genres')} jsonPaths={jsonPaths}
                                      helperText="例如: $.list[0].vod_class (科幻,动画)"/>
                    <JsonPathSelector label="总集数路径" value={detailMapping.totalEpisodes}
                                      onChange={handleMappingChange('totalEpisodes')} jsonPaths={jsonPaths}
                                      helperText="例如: $.list[0].vod_total"/>
                    <JsonPathSelector label="时长路径" value={detailMapping.duration}
                                      onChange={handleMappingChange('duration')} jsonPaths={jsonPaths}
                                      helperText="例如: $.list[0].vod_duration"/>

                    <JsonPathSelector label="分类名称路径 (Type Name)" value={detailMapping.categoryName}
                                      onChange={handleMappingChange('categoryName')} jsonPaths={jsonPaths}
                                      helperText="例如: $.list[0].type_name"/>
                    <JsonPathSelector label="分类ID路径 (Type ID)" value={detailMapping.typeId}
                                      onChange={handleMappingChange('typeId')} jsonPaths={jsonPaths}
                                      helperText="必须映射此字段以支持双层分类体系。例如: $.list[0].type_id"/>

                    <JsonPathSelector label="播放源列表路径 (Play From)" value={detailMapping.playlist_from}
                                      onChange={handleMappingChange('playlist_from')} jsonPaths={jsonPaths}
                                      helperText="例如: $.list[0].vod_play_from"/>
                    <JsonPathSelector label="播放地址列表路径 (Play URL)" value={detailMapping.playlist_url}
                                      onChange={handleMappingChange('playlist_url')} jsonPaths={jsonPaths}
                                      helperText="例如: $.list[0].vod_play_url"/>
                </Grid>
            </Grid>
        </React.Fragment>
    );
}

export default Step3_DetailMapping;
