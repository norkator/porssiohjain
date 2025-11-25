package com.nitramite.porssiohjain.views.components;

import com.vaadin.flow.component.html.Div;

import elemental.json.JsonArray;

public class PriceWindChart extends Div {

    public PriceWindChart() {
        setWidthFull();
        setHeight("250px");
        getStyle().set("position", "relative");
    }

    public void setData(
            JsonArray timestamps,
            JsonArray priceSeries,
            JsonArray secondSeries,
            String priceLabel,
            String secondLabel,
            String xAxisLabel,
            String yAxisLabel,
            String chartTitle,
            String nowLabel
    ) {
        getElement().executeJs("""
                        const container = this;
                        const priceLabel = $3;
                        const secondLabel = $4;
                        const xAxisLabel = $5;
                        const yAxisLabel = $6;
                        const chartTitle = $7;
                        const nowLabel = $8;
                        
                        function renderOrUpdate(dataX, dataY1, dataY2) {
                        
                            if (!window.ApexCharts) {
                                const script = document.createElement('script');
                                script.src = 'https://cdn.jsdelivr.net/npm/apexcharts@3.49.0/dist/apexcharts.min.js';
                                script.onload = () => renderOrUpdate(dataX, dataY1, dataY2);
                                document.head.appendChild(script);
                                return;
                            }
                            
                            if (dataX.length === 0) {
                                return;
                            }
                        
                            const now = new Date();
                            const closest = dataX.reduce((prev, curr) =>
                                Math.abs(new Date(curr) - now) < Math.abs(new Date(prev) - now) ? curr : prev
                            );
                        
                            if (!container.chartInstance) {
                                const options = {
                                    chart: {
                                        type: 'line',
                                        height: '400px',
                                        toolbar: { show: true },
                                        zoom: { enabled: false }
                                    },
                                    series: [
                                        { name: priceLabel, data: dataY1, color: '#0000FF' },
                                        { name: secondLabel, data: dataY2, color: '#FF0000' }
                                    ],
                                    xaxis: {
                                        categories: dataX,
                                        title: { text: xAxisLabel },
                                        labels: { rotate: -45 }
                                    },
                                    yaxis: { title: { text: yAxisLabel } },
                                    title: { text: chartTitle, align: 'center' },
                                    stroke: { curve: 'smooth', width: 2 },
                                    markers: { size: 4 },
                                    tooltip: { shared: true },
                                    annotations: {
                                        xaxis: [
                                            {
                                                x: closest,
                                                borderColor: '#00E396',
                                                label: {
                                                    style: { color: '#fff', background: '#00E396' },
                                                    text: nowLabel
                                                }
                                            }
                                        ]
                                    }
                                };
                        
                                container.chartInstance = new ApexCharts(container, options);
                                container.chartInstance.render();
                        
                            } else {
                                container.chartInstance.updateOptions({
                                    xaxis: { categories: dataX },
                                    annotations: {
                                        xaxis: [
                                            {
                                                x: closest,
                                                borderColor: '#00E396',
                                                label: {
                                                    style: { color: '#fff', background: '#00E396' },
                                                    text: nowLabel
                                                }
                                            }
                                        ]
                                    }
                                });
                        
                                container.chartInstance.updateSeries([
                                    { data: dataY1 },
                                    { data: dataY2 }
                                ], true);
                            }
                        }
                        
                        renderOrUpdate($0, $1, $2);
                        """,
                timestamps, priceSeries, secondSeries,
                priceLabel, secondLabel, xAxisLabel, yAxisLabel, chartTitle, nowLabel
        );
    }
}
