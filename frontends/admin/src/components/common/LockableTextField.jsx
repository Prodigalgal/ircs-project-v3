// File: ircs-frontend/src/components/common/LockableTextField.jsx
import React from 'react';
import { TextField, IconButton, Tooltip } from '@mui/material';
import LockIcon from '@mui/icons-material/Lock';
import LockOpenIcon from '@mui/icons-material/LockOpen';

export function LockToggle({ locked, onLockChange, name, edge = "end", size = "small" }) {
    const handleLockClick = (e) => {
        e.stopPropagation();
        onLockChange(name, !locked);
    };

    return (
        <Tooltip title={locked ? "已锁定 (采集时不会被覆盖)" : "未锁定 (采集时可能被覆盖)"}>
            <IconButton
                aria-label="toggle lock"
                onClick={handleLockClick}
                onMouseDown={(e) => e.preventDefault()}
                edge={edge}
                color={locked ? "error" : "default"}
                size={size}
            >
                {locked ? <LockIcon fontSize="small" /> : <LockOpenIcon fontSize="small" />}
            </IconButton>
        </Tooltip>
    );
}

function LockableTextField({ name, label, value, locked, onChange, onLockChange, slotProps = {}, ...props }) {
    // 强制使用 slotProps.input，不再支持 InputProps
    const inputSlot = slotProps.input || {};
    const originalEndAdornment = inputSlot.endAdornment;

    return (
        <TextField
            name={name}
            label={label}
            value={value}
            onChange={onChange}
            slotProps={{
                ...slotProps,
                input: {
                    ...inputSlot,
                    endAdornment: (
                        <React.Fragment>
                            {originalEndAdornment}
                            <LockToggle
                                locked={locked}
                                onLockChange={onLockChange}
                                name={name}
                            />
                        </React.Fragment>
                    ),
                }
            }}
            {...props}
        />
    );
}

export default LockableTextField;