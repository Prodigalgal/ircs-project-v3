// File: ircs-frontend/src/features/unified-videos/UnifiedVideoForm.jsx
import React, { useEffect } from 'react';
import {
    Autocomplete, Box, Button, Chip, CircularProgress, Dialog, DialogActions,
    DialogContent, DialogTitle, Divider, IconButton, List, ListItem,
    ListItemText, Stack, TextField, Typography, Tabs, Tab
} from '@mui/material';
import Grid from '@mui/material/Grid';
import LinkIcon from '@mui/icons-material/Link';
import WhatshotIcon from '@mui/icons-material/Whatshot';
import LockableTextField, { LockToggle } from '../../components/common/LockableTextField';
import LockControlPanel from '../../components/common/LockControlPanel';
import { useUnifiedVideoForm } from './hooks/useUnifiedVideoForm';
import UnifiedVideoBasicTab from './components/UnifiedVideoBasicTab';
import { useLazyGetCategoriesQuery } from '../categories/categoriesApi';
import { debounce } from '@mui/material/utils';

const AsyncAutocomplete = React.memo(function AsyncAutocomplete({
                                                                    label, placeholder, multiple = false, value, onChange, fetchHook,
                                                                    getOptionLabel = (opt) => opt.name,
                                                                    isOptionEqualToValue = (opt, val) => opt.id === val.id,
                                                                    renderInputEndAdornment,
                                                                    ...props // 透传其他 props
                                                                }) {
    const [open, setOpen] = React.useState(false);
    const [options, setOptions] = React.useState([]);
    const [inputValue, setInputValue] = React.useState('');
    const [triggerFetch, { data, isFetching }] = fetchHook();

    const fetchDebounced = React.useMemo(() => debounce((input) => {
        triggerFetch({ name: input, page: 0, size: 20 });
    }, 400), [triggerFetch]);

    useEffect(() => { if (open) fetchDebounced(inputValue); }, [inputValue, open, fetchDebounced]);
    useEffect(() => { if (data?.content) setOptions(data.content); }, [data]);

    const safeGetOptionLabel = (option) => {
        if (typeof option === 'string') return option;
        return getOptionLabel(option) || '';
    };

    const safeIsOptionEqualToValue = (option, val) => {
        if (typeof val === 'string') return safeGetOptionLabel(option) === val;
        return isOptionEqualToValue(option, val);
    };

    return (
        <Autocomplete
            {...props}
            multiple={multiple}
            open={open}
            onOpen={() => setOpen(true)}
            onClose={() => setOpen(false)}
            onInputChange={(e, val) => setInputValue(val)}
            isOptionEqualToValue={safeIsOptionEqualToValue}
            getOptionLabel={safeGetOptionLabel}
            options={options}
            loading={isFetching}
            value={value}
            onChange={onChange}
            filterOptions={(x) => x}
            freeSolo
            renderInput={(params) => {
                const { InputProps, ...restParams } = params;
                return (
                    <TextField
                        {...restParams} label={label} placeholder={placeholder} variant="outlined"
                        slotProps={{
                            input: {
                                ...InputProps,
                                endAdornment: (
                                    <React.Fragment>
                                        {isFetching ? <CircularProgress color="inherit" size={20} /> : null}
                                        {InputProps.endAdornment}
                                        {renderInputEndAdornment}
                                    </React.Fragment>
                                )
                            }
                        }}
                    />
                );
            }}
        />
    );
});

function TabPanel(props) {
    const { children, value, index, ...other } = props;
    return (
        <div role="tabpanel" hidden={value !== index} {...other}>
            {value === index && <Box sx={{ p: 2 }}>{children}</Box>}
        </div>
    );
}

function UnifiedVideoForm({ open, onClose, onSubmit, unifiedVideo }) {
    const [tabIndex, setTabIndex] = React.useState(0);
    const {
        formData, setFormData, handleChange, handleLockChange, handleBatchLock, isLocked, handleSubmit, isSubmitting,
        setInputValue, options: videoOptions, isSearching, handleAddSourceVideo, handleRemoveSourceVideo,
        lockedCount, totalLockableCount, standardGenres
    } = useUnifiedVideoForm(unifiedVideo, onSubmit);

    useEffect(() => {
        if (open) {
            setTabIndex(0);
        }
    }, [open]);

    if (!open) return null;

    const currentCategory = formData.categoryId
        ? { id: formData.categoryId, name: formData.categoryName || '', slug: formData.categoryCode || '' }
        : null;

    const handleTagsChange = (field) => (event, newValue) => {
        const stringValues = newValue.map(item =>
            typeof item === 'string' ? item : item.name
        );
        setFormData(prev => ({ ...prev, [field]: stringValues }));
    };

    const handleGenreChange = (event, newValue) => {
        const names = newValue.map(item =>
            typeof item === 'string' ? item : item.name
        ).filter(Boolean);
        const codes = newValue.map(item =>
            typeof item === 'string' ? null : item.code
        ).filter(Boolean);
        setFormData(prev => ({ ...prev, genres: names, genreCodes: codes }));
    };

    return (
        <Dialog open={open} onClose={onClose} maxWidth="md" fullWidth>
            <DialogTitle sx={{ display: 'flex', alignItems: 'center', justifyContent: 'space-between' }}>
                <Box>
                    {unifiedVideo ? `编辑聚合视频: ${formData.title}` : '新建聚合视频'}
                </Box>
                {formData.lastTrendAt && (
                    <Chip
                        icon={<WhatshotIcon />}
                        label={`上榜: ${new Date(formData.lastTrendAt).toLocaleDateString()}`}
                        color="error"
                        size="small"
                        variant="outlined"
                    />
                )}
            </DialogTitle>

            <Box sx={{ borderBottom: 1, borderColor: 'divider', px: 2 }}>
                <Tabs value={tabIndex} onChange={(e, v) => setTabIndex(v)}>
                    <Tab label="基本信息" />
                    <Tab label="关联数据" />
                    <Tab label="外部 ID" />
                </Tabs>
            </Box>

            <DialogContent>
                <LockControlPanel
                    lockedCount={lockedCount}
                    totalCount={totalLockableCount}
                    onLockAll={() => handleBatchLock(true)}
                    onUnlockAll={() => handleBatchLock(false)}
                />

                <TabPanel value={tabIndex} index={0}>
                    <UnifiedVideoBasicTab
                        formData={formData}
                        handleChange={handleChange}
                        isLocked={isLocked}
                        handleLockChange={handleLockChange}
                    />
                </TabPanel>

                <TabPanel value={tabIndex} index={1}>
                    <Grid container spacing={2}>
                        <Grid size={{ xs: 12 }}>
                            <AsyncAutocomplete
                                label="分类" placeholder="搜索分类..."
                                fetchHook={useLazyGetCategoriesQuery}
                                value={currentCategory}
                                multiple={false}
                                onChange={(e, val) => {
                                    const catId = typeof val === 'object' && val ? val.id : null;
                                    const catName = typeof val === 'object' && val ? val.name : val;
                                    const catCode = typeof val === 'object' && val ? val.slug : '';
                                    setFormData(prev => ({
                                        ...prev, categoryId: catId, categoryName: catName, categoryCode: catCode
                                    }));
                                }}
                                renderInputEndAdornment={<LockToggle locked={isLocked('category')} name="category" onLockChange={handleLockChange} />}
                            />
                        </Grid>

                        <Grid size={{ xs: 12 }}>
                            <Autocomplete
                                multiple
                                options={standardGenres}
                                getOptionLabel={(option) => typeof option === 'string' ? option : option.name}
                                isOptionEqualToValue={(opt, val) => {
                                    if (typeof val === 'string') return opt.name === val;
                                    return opt.id === val.id;
                                }}
                                value={formData.genres}
                                onChange={handleGenreChange}
                                freeSolo
                                renderInput={(params) => (
                                    <TextField
                                        {...params}
                                        label="题材标签 (Genres)"
                                        placeholder="选择题材"
                                        slotProps={{
                                            ...params.slotProps,

                                            input: {
                                                ...params.slotProps.input,
                                                endAdornment: (
                                                    <>
                                                        {params.slotProps.input.endAdornment}
                                                        <LockToggle locked={isLocked('genres')} name="genres" onLockChange={handleLockChange} />
                                                    </>
                                                )
                                            }
                                        }}
                                    />
                                )}
                            />
                        </Grid>

                        <Grid size={{ xs: 12 }}>
                            <Autocomplete
                                multiple
                                freeSolo
                                options={[]}
                                value={formData.actors}
                                onChange={handleTagsChange('actors')}
                                renderInput={(params) => (
                                    <TextField
                                        {...params}
                                        label="演员"
                                        placeholder="输入演员名称"
                                        slotProps={{
                                            ...params.slotProps,

                                            input: {
                                                ...params.slotProps.input,
                                                endAdornment: (
                                                    <>
                                                        {params.slotProps.input.endAdornment}
                                                        <LockToggle locked={isLocked('actors')} name="actors" onLockChange={handleLockChange} />
                                                    </>
                                                )
                                            }
                                        }}
                                    />
                                )}
                            />
                        </Grid>
                        <Grid size={{ xs: 12 }}>
                            <Autocomplete
                                multiple
                                freeSolo
                                options={[]}
                                value={formData.directors}
                                onChange={handleTagsChange('directors')}
                                renderInput={(params) => (
                                    <TextField
                                        {...params}
                                        label="导演"
                                        placeholder="输入导演名称"
                                        slotProps={{
                                            ...params.slotProps,

                                            input: {
                                                ...params.slotProps.input,
                                                endAdornment: (
                                                    <>
                                                        {params.slotProps.input.endAdornment}
                                                        <LockToggle locked={isLocked('directors')} name="directors" onLockChange={handleLockChange} />
                                                    </>
                                                )
                                            }
                                        }}
                                    />
                                )}
                            />
                        </Grid>

                        <Grid size={{ xs: 12 }}><Divider sx={{ my: 1 }} /></Grid>

                        <Grid size={{ xs: 12 }}>
                            <Typography variant="overline" sx={{ mb: 1, display: 'block' }}>
                                关联源视频 ({formData.sourceVideos.length})
                            </Typography>
                            <Autocomplete
                                freeSolo
                                options={videoOptions}
                                getOptionLabel={(option) => typeof option === 'string' ? option : `${option.title} (${option.year || '-'})`}
                                filterOptions={(x) => x}
                                autoComplete
                                includeInputInList
                                filterSelectedOptions
                                value={null}
                                onChange={handleAddSourceVideo}
                                onInputChange={(event, newInputValue) => setInputValue(newInputValue)}
                                renderInput={(params) => (
                                    <TextField
                                        {...params}
                                        label="搜索并添加源视频..."
                                        placeholder="输入标题关键字搜索"
                                        slotProps={{
                                            ...params.slotProps,

                                            input: {
                                                ...params.slotProps.input,
                                                endAdornment: (
                                                    <>
                                                        {isSearching ? <CircularProgress color="inherit" size={20} /> : null}
                                                        {params.slotProps.input.endAdornment}
                                                    </>
                                                )
                                            }
                                        }}
                                    />
                                )}
                            />
                            <Box sx={{ border: '1px solid rgba(255, 255, 255, 0.12)', borderRadius: 1, mt: 2, maxHeight: 300, overflow: 'auto' }}>
                                <List dense>
                                    {formData.sourceVideos.length === 0 && (
                                        <ListItem><ListItemText primary="暂无关联视频" secondary="请在上方搜索框添加" /></ListItem>
                                    )}
                                    {formData.sourceVideos.map((v) => (
                                        <ListItem
                                            key={v.id}
                                            divider
                                            secondaryAction={
                                                <IconButton edge="end" aria-label="unlink" onClick={() => handleRemoveSourceVideo(v.id)} color="warning">
                                                    <LinkIcon
                                                        sx={{
                                                            fontSize: "small",
                                                            textDecoration: 'line-through'
                                                        }} />
                                                </IconButton>
                                            }
                                        >
                                            <ListItemText primary={v.title} secondary={`SourceVID: ${v.sourceVid || 'N/A'}`} />
                                        </ListItem>
                                    ))}
                                </List>
                            </Box>
                        </Grid>
                    </Grid>
                </TabPanel>

                <TabPanel value={tabIndex} index={2}>
                    <Grid container spacing={2}>
                        {['doubanId', 'tmdbId', 'imdbId', 'rottenTomatoesId'].map(field => (
                            <Grid size={{ xs: 6, sm: 3 }} key={field}>
                                <LockableTextField
                                    name={field} label={field.replace('Id', ' ID').toUpperCase()}
                                    value={formData[field]} onChange={handleChange} fullWidth
                                    locked={isLocked(field)} onLockChange={handleLockChange}
                                />
                            </Grid>
                        ))}
                    </Grid>
                </TabPanel>
            </DialogContent>
            <DialogActions>
                <Button onClick={onClose}>取消</Button>
                <Button onClick={handleSubmit} variant="contained" disabled={isSubmitting}>
                    {isSubmitting ? <CircularProgress size={24} /> : '保存更改'}
                </Button>
            </DialogActions>
        </Dialog>
    );
}

export default UnifiedVideoForm;
