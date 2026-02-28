package com.nitramite.porssiohjain.views.components;

import com.vaadin.flow.component.html.Div;

import java.util.List;

public class EnergyUsagePriceChart extends Div {

    public EnergyUsagePriceChart() {
        setWidthFull();
        setHeight("250px");
        getStyle().set("position", "relative");
    }

    public void setData(
            List<String> timestamps,
            List<Double> usageSeries,
            List<Double> costSeries,
            String usageUnit,
            String currencyUnit,
            String xAxisLabel,
            String chartTitle,
            String nowLabel,
            String usageLabel,
            String costLabel
    ) {
        getElement().executeJs("""
                        const container = this;
                        const usageUnit = $3;
                        const currencyUnit = $4;
                        const xAxisLabel = $5;
                        const chartTitle = $6;
                        const nowLabel = $7;
                        const usageLabel = $8;
                        const costLabel = $9;
                        
                        function renderOrUpdate(dataX, usageY, costY) {
                            if (dataX.length === 0) return;
                        
                            const now = new Date();
                            const closest = dataX.reduce((prev, curr) =>
                                Math.abs(new Date(curr) - now) < Math.abs(new Date(prev) - now) ? curr : prev
                            );
                        
                            const series = [
                                { name: usageLabel, data: usageY, type: 'column' },
                                { name: costLabel, data: costY, type: 'line' }
                            ];
                        
                            if (!container.chartInstance) {
                        
                                const options = {
                                    chart: { height: '400px', toolbar: { show: true }, zoom: { enabled: false } },
                                    series: series,
                                    xaxis: {
                                        categories: dataX,
                                        title: { text: xAxisLabel },
                                        labels: { rotate: -45 }
                                    },
                                    yaxis: [
                                        {
                                            title: { text: usageUnit },
                                            labels: { formatter: val => val.toFixed(2) }
                                        },
                                        {
                                            opposite: true,
                                            title: { text: currencyUnit },
                                            labels: { formatter: val => val.toFixed(2) }
                                        }
                                    ],
                                    title: { text: chartTitle, align: 'center' },
                                    stroke: { curve: 'smooth', width: 3 },
                                    markers: { size: 3 },
                                    tooltip: {
                                        shared: true,
                                        y: [
                                            { formatter: val => val.toFixed(2) + " " + usageUnit },
                                            { formatter: val => val.toFixed(2) + " " + currencyUnit }
                                        ]
                                    },
                                    plotOptions: {
                                        bar: { columnWidth: '60%' }
                                    },
                                    annotations: {
                                        xaxis: [{
                                            x: closest,
                                            borderColor: '#FF4560',
                                            label: {
                                                style: { color: '#fff', background: '#FF4560' },
                                                text: nowLabel
                                            }
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
                                            label: {
                                                style: { color: '#fff', background: '#FF4560' },
                                                text: nowLabel
                                            }
                                        }]
                                    }
                                });
                        
                                container.chartInstance.updateSeries(series, true);
                            }
                        }
                        
                        renderOrUpdate($0, $1, $2);
                        """,
                timestamps, usageSeries, costSeries,
                usageUnit, currencyUnit, xAxisLabel, chartTitle, nowLabel,
                usageLabel, costLabel
        );
    }
}