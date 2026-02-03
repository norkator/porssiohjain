package com.nitramite.porssiohjain.views;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.vaadin.flow.component.AttachEvent;
import com.vaadin.flow.component.ClientCallable;
import com.vaadin.flow.component.dependency.JsModule;
import com.vaadin.flow.component.html.Div;
import com.vaadin.flow.component.html.H2;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.router.PageTitle;
import com.vaadin.flow.router.Route;
import jakarta.annotation.security.PermitAll;

import java.util.Arrays;
import java.util.List;
import java.util.Map;

@PageTitle("Pörssiohjain - Temp curve")
@Route("temperature-curve")
@PermitAll
@JsModule("./js/chart.js")
@JsModule("./js/chartjs-plugin-dragdata.js")
public class TemperatureCurveView extends VerticalLayout {

    public TemperatureCurveView() {
        setSizeFull();
        Div chart = new Div();
        chart.setId("curve-container");
        chart.setHeight("500px");
        add(new H2("Sähkön hinta → Lämpötila käyrä"), chart);
    }

    @Override
    protected void onAttach(AttachEvent attachEvent) {
        super.onAttach(attachEvent);

        double[] curveArray = new double[31];
        Arrays.fill(curveArray, 21);

        StringBuilder jsArray = new StringBuilder("[");
        for (int i = 0; i < curveArray.length; i++) {
            jsArray.append("{\"x\":").append(i).append(",\"y\":").append(curveArray[i]).append("}");
            if (i < curveArray.length - 1) jsArray.append(",");
        }
        jsArray.append("]");

        getElement().executeJs("""
                const container = document.getElementById("curve-container");
                container.innerHTML = "";
                const canvas = document.createElement("canvas");
                canvas.style.width = "100%";
                canvas.style.height = "500px";
                container.appendChild(canvas);
                
                if (window.curveChart) {
                    window.curveChart.destroy();
                    window.curveChart = null;
                }
                window.curveValues = JSON.parse($1);
                
                const ctx = canvas.getContext("2d");
                window.curveChart = new Chart(ctx, {
                    type: "line",
                    data: {
                        datasets: [{
                            label: "Käyrä",
                            data: window.curveValues,
                            tension: 0.3,
                            pointRadius: 6
                        }]
                    },
                    options: {
                        responsive: true,
                        maintainAspectRatio: false,
                        scales: {
                            x: {
                                type: "linear",
                                min: 0,
                                max: 30,
                                ticks: {
                                    stepSize: 1
                                },
                                title: {
                                    display: true,
                                    text: "Sähkön hinta snt/kWh"
                                }
                            },
                            y: {
                                min: 15,
                                max: 28,
                                ticks: { stepSize: 1 },
                                title: {
                                    display: true,
                                    text: "Termostaatin lämpötila (°C)"
                                }
                            }
                        },
                        plugins: {
                            title: { display: false, text: "" },
                            dragData: {
                                round: 1,
                                onDrag: function(e, datasetIndex, index, value) {
                                    window.curveValues[index].y = value.y;
                                },
                                onDragEnd: function(e, datasetIndex, index, value) {
                                    $0.$server.saveCurve(JSON.stringify(window.curveValues));
                                }
                            }
                        }
                    }
                });
                """, getElement(), jsArray.toString()
        );
    }

    @ClientCallable
    public void saveCurve(String jsonArray) {
        ObjectMapper mapper = new ObjectMapper();
        try {
            List<Map<String, Object>> updatedValues = mapper.readValue(
                    jsonArray, new TypeReference<>() {
                    }
            );
            double[] newCurve = new double[updatedValues.size()];
            for (Map<String, Object> point : updatedValues) {
                int x = ((Number) point.get("x")).intValue();
                double y = ((Number) point.get("y")).doubleValue();
                newCurve[x] = y;
            }
            System.out.println(Arrays.toString(newCurve));
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

}
