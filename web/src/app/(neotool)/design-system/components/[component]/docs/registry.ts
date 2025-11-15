import React from 'react';
import { ComponentData, ComponentRendererProps } from './types';

import { buttonData, buttonExamples, ButtonRenderer } from './button';
import { textfieldData, textfieldExamples, TextfieldRenderer } from './textfield';
import { avatarData, avatarExamples, AvatarRenderer } from './avatar';
import { badgeData, badgeExamples, BadgeRenderer } from './badge';
import { chipData, chipExamples, ChipRenderer } from './chip';
import { checkboxfieldData, checkboxfieldExamples, CheckboxRenderer } from './checkboxfield';
import { selectfieldData, selectfieldExamples, SelectRenderer } from './selectfield';
import { currencyfieldData, currencyfieldExamples, CurrencyRenderer } from './currencyfield';
import { togglefieldData, togglefieldExamples, ToggleRenderer } from './togglefield';
import { datepickerfieldData, datepickerfieldExamples, DatepickerRenderer } from './datepickerfield';
import { fileuploaderData, fileuploaderExamples, FileuploaderRenderer } from './fileuploader';
import { autocompletefieldData, autocompletefieldExamples, AutocompleteRenderer } from './autocompletefield';
import { numberfieldData, numberfieldExamples, NumberRenderer } from './numberfield';
import { passwordfieldData, passwordfieldExamples, PasswordRenderer } from './passwordfield';
import { percentfieldData, percentfieldExamples, PercentRenderer } from './percentfield';
import { radiogroupfieldData, radiogroupfieldExamples, RadiogroupRenderer } from './radiogroupfield';
import { tooltipData, tooltipExamples, TooltipRenderer } from './tooltip';
import { linkData, linkExamples, LinkRenderer } from './link';
import { loadingspinnerData, loadingspinnerExamples, LoadingspinnerRenderer } from './loadingspinner';
import { pageskeletonData, pageskeletonExamples, PageskeletonRenderer } from './pageskeleton';
import { drawerData, drawerExamples, DrawerRenderer } from './drawer';
import { ratingData, ratingExamples, RatingRenderer } from './rating';
import { chartData, chartExamples, ChartRenderer } from './chart';
import { asyncautocompleteData, asyncautocompleteExamples, AsyncautocompleteRenderer } from './asyncautocomplete';
import { confirmdialogData, confirmdialogExamples, ConfirmdialogRenderer } from './confirmdialog';
import { emptyerrorstateData, emptyerrorstateExamples, EmptyerrorstateRenderer } from './emptyerrorstate';
import { maskedfieldData, maskedfieldExamples, MaskedRenderer } from './maskedfield';
import { searchfieldData, searchfieldExamples, SearchRenderer } from './searchfield';
import { toastproviderData, toastproviderExamples, ToastproviderRenderer } from './toastprovider';
import { cepfieldData, cepfieldExamples, CepRenderer } from './cepfield';
import { cnpjfieldData, cnpjfieldExamples, CnpjRenderer } from './cnpjfield';
import { cpffieldData, cpffieldExamples, CpfRenderer } from './cpffield';
import { colorpickerData, colorpickerExamples, ColorpickerRenderer } from './colorpicker';
import { sliderData, sliderExamples, SliderRenderer } from './slider';
import { switchData, switchExamples, SwitchRenderer } from './switch';
import { datetimepickerData, datetimepickerExamples, DatetimepickerRenderer } from './datetimepicker';
import { imageuploadData, imageuploadExamples, ImageuploadRenderer } from './imageupload';
import { progressbarData, progressbarExamples, ProgressbarRenderer } from './progressbar';
import { richtexteditorData, richtexteditorExamples, RichtexteditorRenderer } from './richtexteditor';
import { datatableData, datatableExamples, DatatableRenderer } from './datatable';
import { stackData, stackExamples, StackRenderer } from './stack';
import { inlineData, inlineExamples, InlineRenderer } from './inline';
import { clusterData, clusterExamples, ClusterRenderer } from './cluster';
import { gridData, gridExamples, GridRenderer } from './grid';
import { switcherData, switcherExamples, SwitcherRenderer } from './switcher';
import { coverData, coverExamples, CoverRenderer } from './cover';
import { reelData, reelExamples, ReelRenderer } from './reel';
import { frameData, frameExamples, FrameRenderer } from './frame';

export interface ComponentDocs {
  data: ComponentData;
  examples: Record<string, string>;
  renderer: React.ComponentType<ComponentRendererProps>;
}

export const componentRegistry: Record<string, ComponentDocs> = {
  button: {
    data: buttonData,
    examples: buttonExamples,
    renderer: ButtonRenderer,
  },
  textfield: {
    data: textfieldData,
    examples: textfieldExamples,
    renderer: TextfieldRenderer,
  },
  avatar: {
    data: avatarData,
    examples: avatarExamples,
    renderer: AvatarRenderer,
  },
  badge: {
    data: badgeData,
    examples: badgeExamples,
    renderer: BadgeRenderer,
  },
  chip: {
    data: chipData,
    examples: chipExamples,
    renderer: ChipRenderer,
  },
  checkboxfield: {
    data: checkboxfieldData,
    examples: checkboxfieldExamples,
    renderer: CheckboxRenderer,
  },
  selectfield: {
    data: selectfieldData,
    examples: selectfieldExamples,
    renderer: SelectRenderer,
  },
  currencyfield: {
    data: currencyfieldData,
    examples: currencyfieldExamples,
    renderer: CurrencyRenderer,
  },
  togglefield: {
    data: togglefieldData,
    examples: togglefieldExamples,
    renderer: ToggleRenderer,
  },
  datepickerfield: {
    data: datepickerfieldData,
    examples: datepickerfieldExamples,
    renderer: DatepickerRenderer,
  },
  fileuploader: {
    data: fileuploaderData,
    examples: fileuploaderExamples,
    renderer: FileuploaderRenderer,
  },
  autocompletefield: {
    data: autocompletefieldData,
    examples: autocompletefieldExamples,
    renderer: AutocompleteRenderer,
  },
  numberfield: {
    data: numberfieldData,
    examples: numberfieldExamples,
    renderer: NumberRenderer,
  },
  passwordfield: {
    data: passwordfieldData,
    examples: passwordfieldExamples,
    renderer: PasswordRenderer,
  },
  percentfield: {
    data: percentfieldData,
    examples: percentfieldExamples,
    renderer: PercentRenderer,
  },
  radiogroupfield: {
    data: radiogroupfieldData,
    examples: radiogroupfieldExamples,
    renderer: RadiogroupRenderer,
  },
  tooltip: {
    data: tooltipData,
    examples: tooltipExamples,
    renderer: TooltipRenderer,
  },
  link: {
    data: linkData,
    examples: linkExamples,
    renderer: LinkRenderer,
  },
  loadingspinner: {
    data: loadingspinnerData,
    examples: loadingspinnerExamples,
    renderer: LoadingspinnerRenderer,
  },
  pageskeleton: {
    data: pageskeletonData,
    examples: pageskeletonExamples,
    renderer: PageskeletonRenderer,
  },
  drawer: {
    data: drawerData,
    examples: drawerExamples,
    renderer: DrawerRenderer,
  },
  rating: {
    data: ratingData,
    examples: ratingExamples,
    renderer: RatingRenderer,
  },
  chart: {
    data: chartData,
    examples: chartExamples,
    renderer: ChartRenderer,
  },
  asyncautocomplete: {
    data: asyncautocompleteData,
    examples: asyncautocompleteExamples,
    renderer: AsyncautocompleteRenderer,
  },
  confirmdialog: {
    data: confirmdialogData,
    examples: confirmdialogExamples,
    renderer: ConfirmdialogRenderer,
  },
  emptyerrorstate: {
    data: emptyerrorstateData,
    examples: emptyerrorstateExamples,
    renderer: EmptyerrorstateRenderer,
  },
  maskedfield: {
    data: maskedfieldData,
    examples: maskedfieldExamples,
    renderer: MaskedRenderer,
  },
  searchfield: {
    data: searchfieldData,
    examples: searchfieldExamples,
    renderer: SearchRenderer,
  },
  toastprovider: {
    data: toastproviderData,
    examples: toastproviderExamples,
    renderer: ToastproviderRenderer,
  },
  cepfield: {
    data: cepfieldData,
    examples: cepfieldExamples,
    renderer: CepRenderer,
  },
  cnpjfield: {
    data: cnpjfieldData,
    examples: cnpjfieldExamples,
    renderer: CnpjRenderer,
  },
  cpffield: {
    data: cpffieldData,
    examples: cpffieldExamples,
    renderer: CpfRenderer,
  },
  colorpicker: {
    data: colorpickerData,
    examples: colorpickerExamples,
    renderer: ColorpickerRenderer,
  },
  slider: {
    data: sliderData,
    examples: sliderExamples,
    renderer: SliderRenderer,
  },
  switch: {
    data: switchData,
    examples: switchExamples,
    renderer: SwitchRenderer,
  },
  datetimepicker: {
    data: datetimepickerData,
    examples: datetimepickerExamples,
    renderer: DatetimepickerRenderer,
  },
  imageupload: {
    data: imageuploadData,
    examples: imageuploadExamples,
    renderer: ImageuploadRenderer,
  },
  progressbar: {
    data: progressbarData,
    examples: progressbarExamples,
    renderer: ProgressbarRenderer,
  },
  richtexteditor: {
    data: richtexteditorData,
    examples: richtexteditorExamples,
    renderer: RichtexteditorRenderer,
  },
  datatable: {
    data: datatableData,
    examples: datatableExamples,
    renderer: DatatableRenderer,
  },
  stack: {
    data: stackData,
    examples: stackExamples,
    renderer: StackRenderer,
  },
  inline: {
    data: inlineData,
    examples: inlineExamples,
    renderer: InlineRenderer,
  },
  cluster: {
    data: clusterData,
    examples: clusterExamples,
    renderer: ClusterRenderer,
  },
  grid: {
    data: gridData,
    examples: gridExamples,
    renderer: GridRenderer,
  },
  switcher: {
    data: switcherData,
    examples: switcherExamples,
    renderer: SwitcherRenderer,
  },
  cover: {
    data: coverData,
    examples: coverExamples,
    renderer: CoverRenderer,
  },
  reel: {
    data: reelData,
    examples: reelExamples,
    renderer: ReelRenderer,
  },
  frame: {
    data: frameData,
    examples: frameExamples,
    renderer: FrameRenderer,
  },
};

export const getComponentDocs = (name: string): ComponentDocs | null => {
  return componentRegistry[name.toLowerCase()] || null;
};

export const getAllComponentNames = (): string[] => {
  return Object.keys(componentRegistry);
};
