package com.nitramite.porssiohjain.views.components;

import com.vaadin.flow.component.html.Div;
import elemental.json.JsonArray;

public class PriceChart extends Div {

    public PriceChart() {
        setWidthFull();
        setHeight("250px");
        getStyle().set("position", "relative");
    }

    public void setData(
            JsonArray timestamps,
            JsonArray priceSeries,
            String priceLabel,
            String xAxisLabel,
            String yAxisLabel,
            String chartTitle,
            String nowLabel
    ) {
        getElement().executeJs("""
                        const container = this;
                        const priceLabel = $2;
                        const xAxisLabel = $3;
                        const yAxisLabel = $4;
                        const chartTitle = $5;
                        const nowLabel = $6;
                        
                        function renderOrUpdate(dataX, dataY) {
                            if (dataX.length === 0) return;
                        
                            const now = new Date();
                            const closest = dataX.reduce((prev, curr) =>
                                Math.abs(new Date(curr) - now) < Math.abs(new Date(prev) - now) ? curr : prev
                            );
                        
                            if (!container.chartInstance) {
                                const options = {
                                    chart: { type: 'line', height: '400px', toolbar: { show: true }, zoom: { enabled: false } },
                                    series: [{ name: priceLabel, data: dataY, color: '#0000FF' }],
                                    xaxis: { categories: dataX, title: { text: xAxisLabel }, labels: { rotate: -45 } },
                                    yaxis: { title: { text: yAxisLabel } },
                                    title: { text: chartTitle, align: 'center' },
                                    stroke: { curve: 'smooth', width: 2 },
                                    markers: { size: 4 },
                                    tooltip: { shared: true },
                                    annotations: {
                                        xaxis: [{
                                            x: closest,
                                            borderColor: '#00E396',
                                            label: { style: { color: '#fff', background: '#00E396' }, text: nowLabel }
                                        }]
                                    }
                                };
                        
                                container.chartInstance = new ApexCharts(container, options);
                                container.chartInstance.render();
                        
                            } else {
                                container.chartInstance.updateOptions({
                                    xaxis: { categories: dataX },
                                    annotations: {
                                        xaxis: [{
                                            x: closest,
                                            borderColor: '#00E396',
                                            label: { style: { color: '#fff', background: '#00E396' }, text: nowLabel }
                                        }]
                                    }
                                });
                        
                                container.chartInstance.updateSeries([{ data: dataY }], true);
                            }
                        }
                        
                        renderOrUpdate($0, $1);
                        """,
                timestamps, priceSeries, priceLabel, xAxisLabel, yAxisLabel, chartTitle, nowLabel
        );
    }

}
