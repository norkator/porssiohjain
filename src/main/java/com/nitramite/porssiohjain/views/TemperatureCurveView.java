package com.nitramite.porssiohjain.views;

import com.vaadin.flow.component.dependency.JavaScript;
import com.vaadin.flow.component.dependency.JsModule;
import com.vaadin.flow.component.html.Div;
import com.vaadin.flow.component.html.H2;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.router.PageTitle;
import com.vaadin.flow.router.Route;
import jakarta.annotation.security.PermitAll;

@PageTitle("Pörssiohjain - Temp curve")
@Route("temperature-curve")
@PermitAll
@JavaScript("https://cdn.jsdelivr.net/npm/chart.js")
@JavaScript("https://cdn.jsdelivr.net/npm/chartjs-plugin-dragdata")
public class TemperatureCurveView extends VerticalLayout {

    public TemperatureCurveView() {
        setSizeFull();

        Div chart = new Div();
        chart.setId("curve-container");
        chart.setHeight("500px");

        add(new H2("Electricity Price → Temperature Curve"), chart);

        getElement().executeJs("""
                const container = document.getElementById("curve-container");
                container.innerHTML = "";
                const canvas = document.createElement("canvas");
                canvas.style.width = "100%";
                canvas.style.height = "500px";
                container.appendChild(canvas);
                
                new Chart(canvas, {
                  type: "line",
                  data: {
                    datasets: [{
                      data: [
                        { x: 5, y: 23 },
                        { x: 10, y: 22 },
                        { x: 15, y: 20 }
                      ],
                      tension: 0.3,
                      pointRadius: 6
                    }]
                  },
                  options: {
                    responsive: true,
                    maintainAspectRatio: false,
                    scales: {
                      x: { type: "linear", min: 0, max: 30 },
                      y: { min: 16, max: 26 }
                    },
                    plugins: {
                      dragData: { round: 1 }
                    }
                  }
                });
                """);
    }
}
