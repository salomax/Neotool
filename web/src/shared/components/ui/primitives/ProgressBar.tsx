import React from 'react';
import {
  Box,
  Typography,
  LinearProgress,
  CircularProgress,
  Stepper,
  Step,
  StepLabel,
  Chip,
  Stack,
  FormControl,
  FormLabel,
  FormHelperText
} from '@/shared/ui/mui-imports';
import {
  CheckCircleIcon,
  ErrorIcon,
  WarningIcon,
  InfoIcon
} from '@/shared/ui/mui-imports';

export interface ProgressBarProps {
  /** Current progress value (0-100) */
  value?: number;
  /** Whether the progress is indeterminate */
  indeterminate?: boolean;
  /** Progress bar variant */
  variant?: 'linear' | 'circular' | 'step';
  /** Progress bar size */
  size?: 'small' | 'medium' | 'large';
  /** Progress bar color */
  color?: 'primary' | 'secondary' | 'error' | 'warning' | 'info' | 'success';
  /** Progress bar thickness (for circular) */
  thickness?: number;
  /** Progress bar width (for linear) */
  width?: number | string;
  /** Progress bar height (for linear) */
  height?: number;
  /** Whether to show percentage */
  showPercentage?: boolean;
  /** Whether to show label */
  showLabel?: boolean;
  /** Custom label text */
  label?: string;
  /** Helper text below the progress bar */
  helperText?: string;
  /** Error state */
  error?: boolean;
  /** Error message */
  errorMessage?: string;
  /** Whether the progress bar is disabled */
  disabled?: boolean;
  /** Custom CSS class name */
  className?: string;
  /** Test identifier */
  'data-testid'?: string;
}

// Step Progress Props
export interface StepProgressProps extends Omit<ProgressBarProps, 'variant' | 'value'> {
  /** Current step index (0-based) */
  currentStep?: number;
  /** Total number of steps */
  totalSteps?: number;
  /** Step labels */
  steps?: string[];
  /** Whether to show step content */
  showStepContent?: boolean;
  /** Step content */
  stepContent?: React.ReactNode[];
  /** Whether steps are clickable */
  clickable?: boolean;
  /** Callback fired when step is clicked */
  onStepClick?: (stepIndex: number) => void;
  /** Step status */
  stepStatus?: ('completed' | 'active' | 'pending' | 'error')[];
}

const ProgressBar: React.FC<ProgressBarProps | StepProgressProps> = (props) => {
  // Type guard to check if it's step progress
  const isStepProgress = (p: ProgressBarProps | StepProgressProps): p is StepProgressProps => {
    return 'steps' in p || 'currentStep' in p || 'totalSteps' in p;
  };

  const isStep = isStepProgress(props);
  const baseProps = isStep ? {} : props as ProgressBarProps;
  const stepProps = isStep ? props as StepProgressProps : {};

  const variant = isStep ? 'step' : ((baseProps as ProgressBarProps).variant ?? 'linear');
  const size = (isStep ? stepProps.size : baseProps.size) ?? 'medium';
  const color = (isStep ? stepProps.color : baseProps.color) ?? 'primary';
  const thickness = (isStep ? stepProps.thickness : baseProps.thickness) ?? 4;
  const width = isStep ? stepProps.width : baseProps.width;
  const height = (isStep ? stepProps.height : baseProps.height) ?? 8;
  const showPercentage = (isStep ? stepProps.showPercentage : baseProps.showPercentage) ?? true;
  const showLabel = (isStep ? stepProps.showLabel : baseProps.showLabel) ?? true;
  const label = isStep ? stepProps.label : baseProps.label;
  const helperText = isStep ? stepProps.helperText : baseProps.helperText;
  const error = (isStep ? stepProps.error : baseProps.error) ?? false;
  const errorMessage = isStep ? stepProps.errorMessage : baseProps.errorMessage;
  const disabled = (isStep ? stepProps.disabled : baseProps.disabled) ?? false;
  const className = isStep ? stepProps.className : baseProps.className;
  const dataTestId = isStep ? stepProps['data-testid'] : baseProps['data-testid'];

  // Step progress specific props
  const currentStep = isStep ? (stepProps.currentStep ?? 0) : 0;
  const totalSteps = isStep ? (stepProps.totalSteps ?? 3) : 3;
  const steps = isStep ? (stepProps.steps ?? []) : [];
  const showStepContent = isStep ? (stepProps.showStepContent ?? false) : false;
  const stepContent = isStep ? (stepProps.stepContent ?? []) : [];
  const clickable = isStep ? (stepProps.clickable ?? false) : false;
  const onStepClick = isStep ? stepProps.onStepClick : undefined;
  const stepStatus = isStep ? (stepProps.stepStatus ?? []) : [];

  // Only access value if it's not step progress
  const value = isStep ? 0 : (baseProps.value ?? 0);
  const indeterminate = isStep ? false : (baseProps.indeterminate ?? false);

  // Clamp value between 0 and 100
  const clampedValue = Math.min(100, Math.max(0, value));
  
  // Get size values
  const getSizeValues = () => {
    switch (size) {
      case 'small':
        return { linear: 4, circular: 20, step: 24 };
      case 'large':
        return { linear: 12, circular: 60, step: 32 };
      default: // medium
        return { linear: 8, circular: 40, step: 28 };
    }
  };

  const sizeValues = getSizeValues();

  // Get color values
  const getColorValues = () => {
    if (error) return 'error';
    return color;
  };

  const progressColor = getColorValues();

  // Render percentage text
  const renderPercentage = () => {
    if (!showPercentage || indeterminate) return null;
    
    // For step progress, calculate percentage based on current step
    if (variant === 'step') {
      const stepPercentage = totalSteps > 0 ? Math.round((currentStep / totalSteps) * 100) : 0;
      return (
        <Typography
          variant="caption"
          color="text.secondary"
          sx={{ minWidth: '3ch', textAlign: 'right' }}
        >
          {stepPercentage}%
        </Typography>
      );
    }
    
    return (
      <Typography
        variant="caption"
        color="text.secondary"
        sx={{ minWidth: '3ch', textAlign: 'right' }}
      >
        {Math.round(clampedValue)}%
      </Typography>
    );
  };

  // Render label
  const renderLabel = () => {
    if (!showLabel) return null;
    
    const displayLabel = label || (indeterminate ? 'Loading...' : 'Progress');
    
    return (
      <Box sx={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', mb: 1 }}>
        <Typography variant="body2" color="text.primary">
          {displayLabel}
        </Typography>
        {renderPercentage()}
      </Box>
    );
  };

  // Render linear progress
  const renderLinearProgress = () => {
    return (
      <Box sx={{ width: width || '100%' }}>
        {renderLabel()}
        <LinearProgress
          variant={indeterminate ? 'indeterminate' : 'determinate'}
          value={clampedValue}
          color={progressColor}
          sx={{
            height: height,
            borderRadius: height / 2,
            backgroundColor: disabled ? 'action.disabled' : undefined,
            '& .MuiLinearProgress-bar': {
              borderRadius: height / 2,
            },
          }}
        />
      </Box>
    );
  };

  // Render circular progress
  const renderCircularProgress = () => {
    const circularSize = sizeValues.circular;
    
    return (
      <Box sx={{ display: 'flex', flexDirection: 'column', alignItems: 'center', gap: 1 }}>
        {showLabel && (
          <Typography variant="body2" color="text.primary">
            {label || (indeterminate ? 'Loading...' : 'Progress')}
          </Typography>
        )}
        <Box sx={{ position: 'relative', display: 'inline-flex' }}>
          <CircularProgress
            variant={indeterminate ? 'indeterminate' : 'determinate'}
            value={clampedValue}
            color={progressColor}
            size={circularSize}
            thickness={thickness}
            sx={{
              color: disabled ? 'action.disabled' : undefined,
            }}
          />
          {showPercentage && !indeterminate && (
            <Box
              sx={{
                top: 0,
                left: 0,
                bottom: 0,
                right: 0,
                position: 'absolute',
                display: 'flex',
                alignItems: 'center',
                justifyContent: 'center',
              }}
            >
              <Typography
                variant="caption"
                component="div"
                color="text.secondary"
                sx={{ fontSize: '0.75rem', fontWeight: 'bold' }}
              >
                {Math.round(clampedValue)}%
              </Typography>
            </Box>
          )}
        </Box>
      </Box>
    );
  };

  // Render step progress
  const renderStepProgress = () => {
    if (!isStep) return null;
    const { steps, showStepContent, stepContent, clickable, onStepClick, stepStatus = [] } = stepProps;
    
    // Use the currentStep and totalSteps from the main component props
    
    const stepLabels = (steps && steps.length > 0) ? steps : Array.from({ length: totalSteps }, (_, i) => `Step ${i + 1}`);
    
    const getStepIcon = (stepIndex: number) => {
      const safeStepStatus = Array.isArray(stepStatus) ? stepStatus : [];
      const status = safeStepStatus[stepIndex] || (stepIndex < currentStep ? 'completed' : stepIndex === currentStep ? 'active' : 'pending');
      
      switch (status) {
        case 'completed':
          return <CheckCircleIcon color="success" />;
        case 'error':
          return <ErrorIcon color="error" />;
        case 'active':
          return <InfoIcon color="primary" />;
        default:
          return null;
      }
    };

    const getStepColor = (stepIndex: number) => {
      const safeStepStatus = Array.isArray(stepStatus) ? stepStatus : [];
      const status = safeStepStatus[stepIndex] || (stepIndex < currentStep ? 'completed' : stepIndex === currentStep ? 'active' : 'pending');
      
      switch (status) {
        case 'completed':
          return 'success';
        case 'error':
          return 'error';
        case 'active':
          return 'primary';
        default:
          return 'default';
      }
    };

    // Get active step content
    const activeStepContent = showStepContent && stepContent && stepContent[currentStep] ? stepContent[currentStep] : null;

    return (
      <Box>
        {showLabel && (
          <Typography variant="body2" color="text.primary" sx={{ mb: 2 }}>
            {label || `Step ${currentStep + 1} of ${totalSteps}`}
          </Typography>
        )}
        <Stepper
          activeStep={currentStep}
          orientation="horizontal"
          sx={{
            '& .MuiStepLabel-root': {
              cursor: clickable ? 'pointer' : 'default',
            },
          }}
        >
          {stepLabels.map((stepLabel, index) => (
            <Step key={index}>
              <StepLabel
                icon={getStepIcon(index)}
                color={getStepColor(index)}
                onClick={clickable ? () => onStepClick?.(index) : undefined}
                sx={{
                  '& .MuiStepLabel-label': {
                    color: getStepColor(index) === 'default' ? 'text.secondary' : undefined,
                  },
                }}
              >
                {stepLabel}
              </StepLabel>
            </Step>
          ))}
        </Stepper>
        {activeStepContent && (
          <Box sx={{ mt: 3, pt: 2, borderTop: '1px solid', borderColor: 'divider' }}>
            {activeStepContent}
          </Box>
        )}
        {showPercentage && !indeterminate && (
          <Box sx={{ mt: 2, textAlign: 'center' }}>
            <Chip
              label={`${Math.round((currentStep / totalSteps) * 100)}% Complete`}
              color={getStepColor(currentStep)}
              size="small"
            />
          </Box>
        )}
      </Box>
    );
  };

  // Render the appropriate progress variant
  const renderProgress = () => {
    switch (variant) {
      case 'circular':
        return renderCircularProgress();
      case 'step':
        return renderStepProgress();
      default: // linear
        return renderLinearProgress();
    }
  };

  return (
    <FormControl 
      fullWidth 
      disabled={disabled}
      error={error || !!errorMessage}
      {...(className && { className })}
      data-testid={dataTestId}
    >
      {renderProgress()}
      
      {(helperText || errorMessage) && (
        <FormHelperText sx={{ mt: 1 }}>
          {errorMessage || helperText}
        </FormHelperText>
      )}
    </FormControl>
  );
};

export { ProgressBar };
export default ProgressBar;
