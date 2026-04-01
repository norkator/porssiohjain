/*
 * Pörssiohjain - Energy usage optimization platform
 * Copyright (C) 2026  Martin Kankaanranta / Nitramite Tmi
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as
 * published by the Free Software Foundation, version 3.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with this program. If not, see <https://www.gnu.org/licenses/>.
 */

package com.nitramite.porssiohjain.services;

import com.nitramite.porssiohjain.entity.DeviceAcDataEntity;
import com.nitramite.porssiohjain.entity.DeviceEntity;
import com.nitramite.porssiohjain.entity.repository.DeviceAcDataRepository;
import com.nitramite.porssiohjain.entity.repository.DeviceRepository;
import com.nitramite.porssiohjain.services.models.DeviceResponse;
import com.nitramite.porssiohjain.services.toshiba.ToshibaAcStateDecodedResponse;
import com.nitramite.porssiohjain.services.toshiba.ToshibaAcStateHexDecoderService;
import com.nitramite.porssiohjain.services.toshiba.ToshibaAcStateHexEditorService;
import com.nitramite.porssiohjain.services.toshiba.ToshibaAcStateResponse;
import com.nitramite.porssiohjain.services.toshiba.ToshibaAcStateService;
import com.vaadin.flow.component.Component;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.button.ButtonVariant;
import com.vaadin.flow.component.combobox.ComboBox;
import com.vaadin.flow.component.dialog.Dialog;
import com.vaadin.flow.component.formlayout.FormLayout;
import com.vaadin.flow.component.html.Div;
import com.vaadin.flow.component.html.Paragraph;
import com.vaadin.flow.component.notification.Notification;
import com.vaadin.flow.component.notification.NotificationVariant;
import com.vaadin.flow.component.orderedlayout.HorizontalLayout;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.component.textfield.IntegerField;
import com.vaadin.flow.component.textfield.TextArea;
import com.vaadin.flow.component.textfield.TextField;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;

@Service
@RequiredArgsConstructor
public class HeatPumpStateDialogService {

    private final DeviceRepository deviceRepository;
    private final DeviceAcDataRepository deviceAcDataRepository;
    private final ToshibaAcStateService toshibaAcStateService;
    private final ToshibaAcStateHexDecoderService toshibaAcStateHexDecoderService;
    private final ToshibaAcStateHexEditorService toshibaAcStateHexEditorService;
    private final I18nService i18n;

    public void openStateDialog(DeviceResponse deviceResponse, TextField stateHexField) {
        Dialog dialog = new Dialog();
        dialog.setHeaderTitle(t("controlTable.dialog.queryState.title"));
        dialog.setWidth("900px");
        dialog.setMaxWidth("95vw");

        VerticalLayout dialogLayout = new VerticalLayout();
        dialogLayout.setWidthFull();
        dialogLayout.add(new Paragraph(t("controlTable.dialog.queryState.instructions")));

        TextArea editableHexArea = new TextArea(t("controlTable.dialog.queryState.field.stateHex"));
        editableHexArea.setWidthFull();
        editableHexArea.setMinHeight("110px");

        ComboBox<PowerOption> powerField = new ComboBox<>(t("controlTable.dialog.queryState.editor.power"));
        powerField.setItems(PowerOption.values());
        powerField.setItemLabelGenerator(PowerOption::label);
        powerField.setClearButtonVisible(true);
        powerField.setWidthFull();

        ComboBox<ToshibaAcStateHexEditorService.EditableMode> modeField = new ComboBox<>(t("controlTable.dialog.queryState.editor.mode"));
        modeField.setItems(ToshibaAcStateHexEditorService.EditableMode.values());
        modeField.setItemLabelGenerator(ToshibaAcStateHexEditorService.EditableMode::label);
        modeField.setClearButtonVisible(true);
        modeField.setWidthFull();

        IntegerField targetTemperatureField = new IntegerField(t("controlTable.dialog.queryState.editor.targetTemperature"));
        targetTemperatureField.setWidthFull();
        targetTemperatureField.setStepButtonsVisible(true);
        targetTemperatureField.setMin(ToshibaAcStateHexEditorService.MIN_TARGET_TEMPERATURE);
        targetTemperatureField.setMax(ToshibaAcStateHexEditorService.MAX_TARGET_TEMPERATURE);
        targetTemperatureField.setHelperText(t(
                "controlTable.dialog.queryState.editor.targetTemperatureHelper",
                ToshibaAcStateHexEditorService.MIN_TARGET_TEMPERATURE,
                ToshibaAcStateHexEditorService.MAX_TARGET_TEMPERATURE
        ));

        FormLayout editorLayout = new FormLayout(powerField, modeField, targetTemperatureField);
        editorLayout.setWidthFull();
        editorLayout.setResponsiveSteps(
                new FormLayout.ResponsiveStep("0", 1),
                new FormLayout.ResponsiveStep("700px", 3)
        );

        Div stateInfoDiv = new Div();
        stateInfoDiv.setVisible(false);
        stateInfoDiv.setWidthFull();

        boolean[] syncing = new boolean[]{false};

        Runnable refreshPreview = () -> {
            String currentHex = editableHexArea.getValue();
            if (currentHex == null || currentHex.isBlank()) {
                stateInfoDiv.removeAll();
                stateInfoDiv.setVisible(false);
                return;
            }

            ToshibaAcStateResponse response = buildDecodedResponse(currentHex);
            stateInfoDiv.removeAll();
            stateInfoDiv.add(createAcStateInfoContent(response));
            stateInfoDiv.setVisible(true);
        };

        java.util.function.Consumer<String> renderHexState = hexState -> {
            syncing[0] = true;
            try {
                editableHexArea.setValue(Optional.ofNullable(hexState).orElse(""));
                if (hexState == null || hexState.isBlank()) {
                    powerField.clear();
                    modeField.clear();
                    targetTemperatureField.clear();
                    stateHexField.clear();
                    stateInfoDiv.removeAll();
                    stateInfoDiv.setVisible(false);
                    return;
                }

                ToshibaAcStateResponse response = buildDecodedResponse(hexState);
                ToshibaAcStateDecodedResponse decoded = response.getResObj() != null ? response.getResObj().getDecodedAcState() : null;
                String normalizedHex = decoded != null && decoded.getNormalizedHex() != null
                        ? decoded.getNormalizedHex()
                        : hexState;

                editableHexArea.setValue(normalizedHex);
                stateHexField.setValue(normalizedHex);
                syncEditorFields(decoded, powerField, modeField, targetTemperatureField);
                stateInfoDiv.removeAll();
                stateInfoDiv.add(createAcStateInfoContent(response));
                stateInfoDiv.setVisible(true);
            } finally {
                syncing[0] = false;
            }
        };

        Runnable applyEditorChanges = () -> {
            if (syncing[0]) {
                return;
            }
            try {
                String updatedHex = toshibaAcStateHexEditorService.applyEditableSettings(
                        editableHexArea.getValue(),
                        powerField.getValue() != null ? powerField.getValue().powerOn() : null,
                        modeField.getValue(),
                        targetTemperatureField.getValue()
                );
                renderHexState.accept(updatedHex);
            } catch (Exception ex) {
                Notification.show(t("controlTable.notification.failedSave", ex.getMessage()))
                        .addThemeVariants(NotificationVariant.LUMO_ERROR);
            }
        };

        editableHexArea.addValueChangeListener(event -> {
            if (syncing[0]) {
                return;
            }
            String value = event.getValue();
            stateHexField.setValue(Optional.ofNullable(value).orElse(""));
            try {
                String normalizedHex = toshibaAcStateHexEditorService.normalizeEditableHex(value);
                renderHexState.accept(normalizedHex);
            } catch (IllegalArgumentException ex) {
                refreshPreview.run();
            }
        });

        powerField.addValueChangeListener(event -> applyEditorChanges.run());
        modeField.addValueChangeListener(event -> applyEditorChanges.run());
        targetTemperatureField.addValueChangeListener(event -> applyEditorChanges.run());

        Button acquireCurrentStateButton = new Button(t("controlTable.dialog.queryState.actionButton"), event -> {
            try {
                DeviceAcDataEntity acData = getAcData(deviceResponse);
                ToshibaAcStateResponse response = toshibaAcStateService.getAcState(acData);
                if (response != null && response.isSuccess() && response.getResObj() != null) {
                    renderHexState.accept(response.getResObj().getAcStateData());
                    Notification.show(t("controlTable.dialog.queryState.queried"))
                            .addThemeVariants(NotificationVariant.LUMO_SUCCESS);
                } else {
                    Notification.show(t("controlTable.notification.failedSave", response != null ? response.getMessage() : "Empty response"))
                            .addThemeVariants(NotificationVariant.LUMO_ERROR);
                }
            } catch (Exception ex) {
                Notification.show(t("controlTable.notification.failedSave", ex.getMessage()))
                        .addThemeVariants(NotificationVariant.LUMO_ERROR);
            }
        });

        Button useLastPolledStateButton = new Button(t("controlTable.dialog.queryState.useLastPolledButton"), event -> {
            try {
                DeviceAcDataEntity acData = getAcData(deviceResponse);
                if (acData.getLastPolledStateHex() == null || acData.getLastPolledStateHex().isBlank()) {
                    Notification.show(t("controlTable.dialog.queryState.noLastPolledState"))
                            .addThemeVariants(NotificationVariant.LUMO_ERROR);
                    return;
                }
                renderHexState.accept(acData.getLastPolledStateHex());
                Notification.show(t("controlTable.dialog.queryState.loadedLastPolled"))
                        .addThemeVariants(NotificationVariant.LUMO_SUCCESS);
            } catch (Exception ex) {
                Notification.show(t("controlTable.notification.failedSave", ex.getMessage()))
                        .addThemeVariants(NotificationVariant.LUMO_ERROR);
            }
        });
        useLastPolledStateButton.addThemeVariants(ButtonVariant.LUMO_TERTIARY);

        HorizontalLayout buttonRow = new HorizontalLayout(acquireCurrentStateButton, useLastPolledStateButton);
        buttonRow.setWidthFull();
        buttonRow.setWrap(true);

        dialogLayout.add(
                buttonRow,
                new Paragraph(t("controlTable.dialog.queryState.editor.instructions")),
                editableHexArea,
                editorLayout,
                stateInfoDiv
        );
        dialog.add(dialogLayout);

        String existingState = stateHexField.getValue();
        if (existingState != null && !existingState.isBlank()) {
            renderHexState.accept(existingState);
        }

        Button saveButton = new Button(t("common.save"), event -> {
            try {
                String normalizedHex = toshibaAcStateHexEditorService.normalizeEditableHex(editableHexArea.getValue());
                stateHexField.setValue(normalizedHex);
                dialog.close();
            } catch (Exception ex) {
                Notification.show(t("controlTable.notification.failedSave", ex.getMessage()))
                        .addThemeVariants(NotificationVariant.LUMO_ERROR);
            }
        });
        saveButton.addThemeVariants(ButtonVariant.LUMO_PRIMARY);
        dialog.getFooter().add(saveButton);

        Button cancelButton = new Button(t("common.cancel"), event -> {
            stateHexField.clear();
            dialog.close();
        });
        dialog.getFooter().add(cancelButton);

        dialog.open();
    }

    public Component createAcStateInfoContentFromHex(String stateHex) {
        return createAcStateInfoContent(buildDecodedResponse(stateHex));
    }

    public Component createAcStateInfoContent(ToshibaAcStateResponse response) {
        VerticalLayout content = new VerticalLayout();
        content.setSpacing(true);
        content.setPadding(false);
        content.setWidthFull();

        ToshibaAcStateResponse.ResObj resObj = response.getResObj();
        ToshibaAcStateDecodedResponse decoded = resObj.getDecodedAcState();

        TextArea hexArea = new TextArea(t("controlTable.dialog.queryState.field.stateHex"));
        hexArea.setValue(Optional.ofNullable(resObj.getAcStateData()).orElse(""));
        hexArea.setReadOnly(true);
        hexArea.setWidthFull();
        content.add(hexArea);

        if (decoded == null) {
            content.add(new Paragraph(t("controlTable.dialog.queryState.decodedUnavailable")));
            return content;
        }

        TextField summaryField = createReadOnlyField(t("controlTable.dialog.queryState.field.summary"), decoded.getSummary());
        TextField validField = createReadOnlyField(t("controlTable.dialog.queryState.field.valid"), String.valueOf(decoded.isValid()));
        TextField normalizedHexField = createReadOnlyField(t("controlTable.dialog.queryState.field.normalizedHex"), decoded.getNormalizedHex());
        TextField byteLengthField = createReadOnlyField(
                t("controlTable.dialog.queryState.field.byteLength"),
                decoded.getByteLength() != null ? String.valueOf(decoded.getByteLength()) : null
        );

        FormLayout metaLayout = new FormLayout(summaryField, validField, normalizedHexField, byteLengthField);
        metaLayout.setWidthFull();
        metaLayout.setResponsiveSteps(
                new FormLayout.ResponsiveStep("0", 1),
                new FormLayout.ResponsiveStep("600px", 2)
        );
        metaLayout.setColspan(summaryField, 2);
        metaLayout.setColspan(normalizedHexField, 2);
        content.add(metaLayout);

        FormLayout decodedLayout = new FormLayout(
                createDecodedValueField(t("controlTable.dialog.queryState.field.power"), decoded.getPower()),
                createDecodedValueField(t("controlTable.dialog.queryState.field.mode"), decoded.getMode()),
                createTemperatureField(t("controlTable.dialog.queryState.field.targetTemperature"), decoded.getTargetTemperature()),
                createDecodedValueField(t("controlTable.dialog.queryState.field.fanMode"), decoded.getFanMode()),
                createDecodedValueField(t("controlTable.dialog.queryState.field.swingMode"), decoded.getSwingMode()),
                createDecodedValueField(t("controlTable.dialog.queryState.field.powerSelection"), decoded.getPowerSelection()),
                createDecodedValueField(t("controlTable.dialog.queryState.field.meritB"), decoded.getMeritB()),
                createDecodedValueField(t("controlTable.dialog.queryState.field.meritA"), decoded.getMeritA()),
                createDecodedValueField(t("controlTable.dialog.queryState.field.airPureIon"), decoded.getAirPureIon()),
                createTemperatureField(t("controlTable.dialog.queryState.field.indoorTemperature"), decoded.getIndoorTemperature()),
                createTemperatureField(t("controlTable.dialog.queryState.field.outdoorTemperature"), decoded.getOutdoorTemperature()),
                createDecodedValueField(t("controlTable.dialog.queryState.field.selfCleaning"), decoded.getSelfCleaning())
        );
        decodedLayout.setWidthFull();
        decodedLayout.setResponsiveSteps(
                new FormLayout.ResponsiveStep("0", 1),
                new FormLayout.ResponsiveStep("600px", 2),
                new FormLayout.ResponsiveStep("900px", 3)
        );
        content.add(decodedLayout);

        TextArea warningsArea = createReadOnlyTextArea(t("controlTable.dialog.queryState.field.warnings"), String.join("\n", decoded.getWarnings()));
        TextArea unknownFieldsArea = createReadOnlyTextArea(t("controlTable.dialog.queryState.field.unknownFields"), formatUnknownFields(decoded.getUnknownFields()));
        TextArea rawBytesArea = createReadOnlyTextArea(t("controlTable.dialog.queryState.field.rawBytes"), formatRawBytes(decoded.getRawBytes()));

        content.add(warningsArea, unknownFieldsArea, rawBytesArea);
        return content;
    }

    private void syncEditorFields(
            ToshibaAcStateDecodedResponse decoded,
            ComboBox<PowerOption> powerField,
            ComboBox<ToshibaAcStateHexEditorService.EditableMode> modeField,
            IntegerField targetTemperatureField
    ) {
        if (decoded == null) {
            powerField.clear();
            modeField.clear();
            targetTemperatureField.clear();
            return;
        }

        powerField.setValue(PowerOption.fromDecoderCode(decoded.getPower() != null ? decoded.getPower().getCode() : null));
        modeField.setValue(ToshibaAcStateHexEditorService.EditableMode.fromDecoderCode(decoded.getMode() != null ? decoded.getMode().getCode() : null));
        if (decoded.getTargetTemperature() != null && decoded.getTargetTemperature().isAvailable()) {
            targetTemperatureField.setValue(decoded.getTargetTemperature().getTemperatureCelsius());
        } else {
            targetTemperatureField.clear();
        }
    }

    private DeviceAcDataEntity getAcData(DeviceResponse deviceResponse) {
        DeviceEntity deviceEntity = deviceRepository.findById(deviceResponse.getId())
                .orElseThrow(() -> new IllegalArgumentException("Device not found"));
        return deviceAcDataRepository.findByDevice(deviceEntity)
                .orElseThrow(() -> new IllegalArgumentException("AC data not found for device"));
    }

    private ToshibaAcStateResponse buildDecodedResponse(String stateHex) {
        ToshibaAcStateResponse response = new ToshibaAcStateResponse();
        response.setSuccess(true);

        ToshibaAcStateResponse.ResObj resObj = new ToshibaAcStateResponse.ResObj();
        resObj.setAcStateData(stateHex);
        resObj.setDecodedAcState(toshibaAcStateHexDecoderService.decode(stateHex));
        response.setResObj(resObj);
        return response;
    }

    private TextField createDecodedValueField(String label, ToshibaAcStateDecodedResponse.DecodedValue value) {
        if (value == null) {
            return createReadOnlyField(label, "");
        }
        String text = value.getLabel();
        if (value.getCode() != null && !value.getCode().isBlank()) {
            text = (text == null || text.isBlank() ? "" : text + " | ") + value.getCode();
        }
        if (value.getRawHex() != null && !value.getRawHex().isBlank()) {
            text = (text == null || text.isBlank() ? "" : text + " | ") + value.getRawHex();
        }
        return createReadOnlyField(label, text);
    }

    private TextField createTemperatureField(String label, ToshibaAcStateDecodedResponse.TemperatureValue value) {
        if (value == null) {
            return createReadOnlyField(label, "");
        }
        String text = value.getLabel();
        if (value.getRawHex() != null && !value.getRawHex().isBlank()) {
            text = (text == null || text.isBlank() ? "" : text + " | ") + value.getRawHex();
        }
        return createReadOnlyField(label, text);
    }

    private TextField createReadOnlyField(String label, String value) {
        TextField field = new TextField(label);
        field.setReadOnly(true);
        field.setWidthFull();
        field.setValue(value != null ? value : "");
        return field;
    }

    private TextArea createReadOnlyTextArea(String label, String value) {
        TextArea area = new TextArea(label);
        area.setReadOnly(true);
        area.setWidthFull();
        area.setValue(value != null && !value.isBlank() ? value : "-");
        area.setMinHeight("120px");
        return area;
    }

    private String formatUnknownFields(List<ToshibaAcStateDecodedResponse.UnknownFieldValue> unknownFields) {
        if (unknownFields == null || unknownFields.isEmpty()) {
            return "";
        }
        return unknownFields.stream()
                .map(field -> String.format(
                        "%s: index=%d, raw=%s, unsigned=%s, signed=%s, note=%s",
                        field.getField(),
                        field.getIndex(),
                        field.getRawHex(),
                        field.getRawUnsigned(),
                        field.getRawSigned(),
                        field.getNote()
                ))
                .reduce((a, b) -> a + "\n" + b)
                .orElse("");
    }

    private String formatRawBytes(List<ToshibaAcStateDecodedResponse.RawByteValue> rawBytes) {
        if (rawBytes == null || rawBytes.isEmpty()) {
            return "";
        }
        return rawBytes.stream()
                .map(rawByte -> String.format(
                        "[%d] %s | unsigned=%s | signed=%s | %s",
                        rawByte.getIndex(),
                        rawByte.getRawHex(),
                        rawByte.getRawUnsigned(),
                        rawByte.getRawSigned(),
                        rawByte.getMeaning()
                ))
                .reduce((a, b) -> a + "\n" + b)
                .orElse("");
    }

    private String t(String key, Object... args) {
        return i18n.t(key, args);
    }

    private enum PowerOption {
        ON("On", true, "ON"),
        OFF("Off", false, "OFF");

        private final String label;
        private final boolean powerOn;
        private final String decoderCode;

        PowerOption(String label, boolean powerOn, String decoderCode) {
            this.label = label;
            this.powerOn = powerOn;
            this.decoderCode = decoderCode;
        }

        public String label() {
            return label;
        }

        public boolean powerOn() {
            return powerOn;
        }

        public static PowerOption fromDecoderCode(String decoderCode) {
            if (decoderCode == null || decoderCode.isBlank()) {
                return null;
            }
            for (PowerOption option : values()) {
                if (option.decoderCode.equalsIgnoreCase(decoderCode)) {
                    return option;
                }
            }
            return null;
        }
    }
}
