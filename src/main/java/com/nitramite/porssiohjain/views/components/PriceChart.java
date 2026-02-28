package com.nitramite.porssiohjain.views.components;

import com.vaadin.flow.component.html.Div;

import java.util.List;

public class PriceChart extends Div {

    public PriceChart() {
        setWidthFull();
        setHeight("250px");
        getStyle().set("position", "relative");
    }

    public void setData(
            List<String> timestamps,
            List<Double> windSeries,
            List<Double> priceSeries,
            String windUnit,
            String priceUnit,
            String xAxisLabel,
            String chartTitle,
            String nowLabel,
            String windForecastLabel,
            String pricePredictionLabel
    ) {
        getElement().executeJs("""
                        const container = this;
                        const windUnit = $3;
                        const priceUnit = $4;
                        const xAxisLabel = $5;
                        const chartTitle = $6;
                        const nowLabel = $7;
                        const windForecastLabel = $8;
                        const pricePredictionLabel = $9;
                        
                        function renderOrUpdate(dataX, windY, priceY) {
                            if (dataX.length === 0) return;
                        
                            const now = new Date();
                            const closest = dataX.reduce((prev, curr) =>
                                Math.abs(new Date(curr) - now) < Math.abs(new Date(prev) - now) ? curr : prev
                            );
                        
                            const series = [
                                { name: windForecastLabel, data: windY, type: 'line' },
                                { name: pricePredictionLabel, data: priceY, type: 'line' }
                            ];
                        
                            if (!container.chartInstance) {
                                const options = {
                                    chart: { height: '400px', toolbar: { show: true }, zoom: { enabled: false } },
                                    series: series,
                                    xaxis: { categories: dataX, title: { text: xAxisLabel }, labels: { rotate: -45 } },
                                    yaxis: [
                                        {
                                            title: { text: windUnit },
                                            labels: { formatter: val => val.toFixed(0) }
                                        },
                                        {
                                            opposite: true,
                                            title: { text: priceUnit },
                                            labels: { formatter: val => val.toFixed(2) }
                                        }
                                    ],
                                    title: { text: chartTitle, align: 'center' },
                                    stroke: { curve: 'smooth', width: 2 },
                                    markers: { size: 3 },
                                    tooltip: { shared: true },
                                    annotations: {
                                        xaxis: [{
                                            x: closest,
                                            borderColor: '#FF4560',
                                            label: { style: { color: '#fff', background: '#FF4560' }, text: nowLabel }
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
                                            borderColor: '#FF4560',
                                            label: { style: { color: '#fff', background: '#FF4560' }, text: nowLabel }
                                        }]
                                    }
                                });
                        
                                container.chartInstance.updateSeries(series, true);
                            }
                        }
                        
                        renderOrUpdate($0, $1, $2);
                        """,
                timestamps, windSeries, priceSeries, windUnit, priceUnit, xAxisLabel, chartTitle, nowLabel,
                windForecastLabel, pricePredictionLabel
        );
    }
}
