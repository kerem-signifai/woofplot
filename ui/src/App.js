import React, {Component} from 'react';
import {Button, Dropdown} from 'semantic-ui-react';
import {Line} from 'react-chartjs-2';
import Chart from 'chart.js';

import 'chartjs-plugin-colorschemes';
import './App.css';

class App extends Component {
    constructor(props) {
        super(props);
        this.state = {
            isFetching: false,
            isLoading: false,
            availableWoofs: [],
            selectedWoofs: [],
            retentionMinutes: 60,
            loadedRetentionMinutes: 60,
            datasets: [],
            scaleModifier: 1,
        };
    }

    getState() {
        return this.state
    }

    componentDidMount() {
        const that = this;
        Chart.plugins.register({
            afterDraw: function (chart) {
                const chartContainer = document.getElementById('chart-wrapper');
                const dataCanvas = document.getElementById('data-chart');
                const cursorCanvas = document.getElementById('cursor');
                const cursorCtx = cursorCanvas.getContext('2d');

                chartContainer.onmousemove = (e) => {
                    const dataWidth = parseInt(dataCanvas.style.width, 10);
                    const dataHeight = parseInt(dataCanvas.style.height, 10);
                    cursorCanvas.width = dataWidth;
                    cursorCanvas.height = dataHeight;
                    const {offsetLeft, offsetTop} = cursorCanvas;
                    const {clientX, clientY} = e;
                    cursorCtx.clearRect(0, 0, cursorCanvas.width, cursorCanvas.height);
                    if (clientX <= offsetLeft + dataWidth && clientX >= 48 && clientY <= offsetTop + dataHeight && clientY >= offsetTop) {
                        cursorCtx.beginPath();
                        cursorCtx.moveTo(clientX - offsetLeft, that.state.datasets.length > 0 ? 32 : 10);
                        cursorCtx.lineTo(clientX - offsetLeft, dataHeight - 30);
                        cursorCtx.stroke();
                    }
                };
                chartContainer.onmouseleave = () => {
                    cursorCtx.clearRect(0, 0, cursorCanvas.width, cursorCanvas.height);
                };

                chartContainer.onwheel = (e) => {
                    Object.values(chart.scales).filter(scale => scale.position === 'left' || scale.position === 'right').forEach(scale => {
                    })
                };
            }
        });
        this.setState({isFetching: true});
        fetch('http://localhost:8080/api/woofs')
            .then(response => {
                return response.json();
            })
            .then(results => {
                const loaded = results.flatMap(woof =>
                    woof.dataTypes.map(dataType => ({
                        woof: woof,
                        dataType: dataType
                    }))
                );
                const woofs = Object.assign({}, ...loaded.map(entry => {
                    const woof = entry.woof;
                    const dataType = entry.dataType;
                    const id = woof.sourceId + '_' + dataType;
                    return {
                        [id]: {
                            key: id,
                            value: id,
                            text: woof.name + ' [' + dataType + ']',
                            source: woof
                        }
                    }
                }));
                console.log(woofs);
                this.setState({availableWoofs: woofs, isFetching: false});
            });
    }

    handleLoad = () => {
        const {selectedWoofs, retentionMinutes, availableWoofs} = this.state;

        const aggIntervals = [
            {
                geq: 6 * 30 * 24 * 60,
                interval: 'week'
            },
            {
                geq: 30 * 24 * 60,
                interval: 'day'
            },
            {
                geq: 7 * 24 * 60,
                interval: 'hour'
            },
            {
                geq: 0,
                interval: 'minute'
            }
        ];

        const interval = aggIntervals.find(cfg => retentionMinutes >= cfg.geq).interval;

        this.setState({isLoading: true});
        Promise.all(
            selectedWoofs.map(woofId =>
                fetch(
                    `http://localhost:8080/api/query?woofId=${woofId}&from=${Date.now() - retentionMinutes * 60 * 1000}&to=${Date.now()}&aggregation=average&interval=${interval}`
                ).then(response => response.json())
                    .then(results => {
                        const woofRef = availableWoofs[woofId];
                        return {
                            label: woofRef.text,
                            fill: false,
                            borderJoinStyle: 'round',
                            lineTension: 0,
                            pointRadius: 3,
                            pointHoverRadius: 6,
                            data: results.map(woof => {
                                return {
                                    x: woof.timestamp,
                                    y: woof.value
                                };
                            })
                        };
                    })
            )
        ).then(results => {
            this.setState({
                isLoading: false,
                datasets: results,
                loadedRetentionMinutes: retentionMinutes,
                scaleModifier: 1
            });
        });
    };

    handleRangeSelect = (event, data) => {
        this.setState({retentionMinutes: data.value});
    };

    handleSourceSelect = (event, data) => {
        this.setState({selectedWoofs: data.value});
    };

    handleWheel = (e) => {
        this.setState({
            scaleModifier: Math.max(1, this.state.scaleModifier + e.deltaY / 5000.0)
        });
    };

    render() {
        const {isFetching, isLoading, availableWoofs, datasets, loadedRetentionMinutes, scaleModifier} = this.state;

        const data = {datasets: datasets};
        const dateFormats = [
            {
                geq: 6 * 30 * 24 * 60,
                unit: 'month'
            },
            {
                geq: 3 * 24 * 60,
                unit: 'day'
            },
            {
                geq: 0,
                unit: 'hour'
            }
        ];

        const format = dateFormats.find(cfg => loadedRetentionMinutes >= cfg.geq);

        const customFormat = ('formatStr' in format) ? {[format.unit]: format.formatStr} : {};

        const options = {
            plugins: {
                colorschemes: {
                    scheme: 'office.Berlin6'
                }
            },
            responsive: true,
            tooltips: {
                mode: 'nearest',
                intersect: false,
                axis: 'x'
            },
            hover: {
                mode: 'nearest',
                intersect: false,
                axis: 'x'
            },
            scales: {
                yAxes: [
                    {
                        scaleLabel: {
                            display: true,
                            labelString: 'Temperature',
                            fontSize: 20
                        },
                        afterDataLimits: (axis) => {
                            axis.max *= scaleModifier;
                            axis.min /= scaleModifier;
                        }
                    }
                ],
                xAxes: [
                    {
                        type: 'time',
                        bounds: 'ticks',
                        time: {
                            displayFormats: customFormat,
                            unit: format.unit
                        },
                        ticks: {
                            maxTicksLimit: 12,
                            autoSkip: true,
                            min: Date.now() - (loadedRetentionMinutes * 60 * 1000),
                            max: Date.now()
                        },
                        distribution: 'linear'
                    }
                ]
            }
        };

        const ranges = [
            {
                value: 60,
                text: 'Past hour'
            },
            {
                value: 6 * 60,
                text: 'Past 6 hours'
            },
            {
                value: 24 * 60,
                text: 'Past day'
            },
            {
                value: 7 * 24 * 60,
                text: 'Past week'
            },
            {
                value: 31 * 24 * 60,
                text: 'Past month'
            },
            {
                value: 365 * 24 * 60,
                text: 'Past year'
            }
        ];

        // const intervals = [
        //     {
        //         value: 'minute',
        //         text: 'minute'
        //     },
        //     {
        //         value: 'hour',
        //         text: 'hour'
        //     },
        //     {
        //         value: 'day',
        //         text: 'day'
        //     },
        //     {
        //         value: 'week',
        //         text: 'week'
        //     },
        //     {
        //         value: 'month',
        //         text: 'month'
        //     }
        // ];
        //
        // const aggregations = [
        //     {
        //         value: 'average',
        //         text: 'average'
        //     },
        //     {
        //         value: 'count',
        //         text: 'count'
        //     },
        //     {
        //         value: 'max',
        //         text: 'max'
        //     },
        //     {
        //         value: 'min',
        //         text: 'min'
        //     },
        //     {
        //         value: 'sum',
        //         text: 'sum'
        //     }
        // ];

        return (
            <div className='container'>
                <span className='datasource-select'>
                    <Dropdown
                        placeholder='Data source'
                        fluid
                        multiple
                        search
                        selection
                        loading={isFetching || isLoading}
                        disabled={isFetching || isLoading}
                        options={Object.values(availableWoofs)}
                        onChange={this.handleSourceSelect}
                    />
                </span>
                <span className='range-select'>
                    <Dropdown
                        placeholder='Range'
                        fluid
                        selection
                        loading={isLoading}
                        disabled={isLoading}
                        options={ranges}
                        onChange={this.handleRangeSelect}
                        defaultValue={ranges[0].value}
                    />
                </span>
                {/*<div className='agg-interval-select'>*/}
                {/*  <Dropdown*/}
                {/*      placeholder='Aggregation interval'*/}
                {/*      fluid*/}
                {/*      selection*/}
                {/*      loading={isLoading}*/}
                {/*      disabled={isLoading}*/}
                {/*      options={intervals}*/}
                {/*      onChange={this.handleIntervalSelect}*/}
                {/*      defaultValue={intervals[0].value}*/}
                {/*  />*/}
                {/*</div>*/}
                {/*<div className='agg-type-select'>*/}
                {/*  <Dropdown*/}
                {/*      placeholder='Aggregation type'*/}
                {/*      fluid*/}
                {/*      selection*/}
                {/*      loading={isLoading}*/}
                {/*      disabled={isLoading}*/}
                {/*      options={aggregations}*/}
                {/*      onChange={this.handleAggregationSelect}*/}
                {/*      defaultValue={aggregations[0].value}*/}
                {/*  />*/}
                {/*</div>*/}
                <span className='plot-button'>
                    <Button
                        loading={isFetching || isLoading}
                        disabled={isFetching || isLoading}
                        onClick={this.handleLoad}
                    >
                        Plot
                    </Button>
                </span>
                <br style={{clear: 'both'}}/>
                <div className='chart-container' id='chart-wrapper' onWheel={(e) => this.handleWheel(e)}>

                    <canvas
                        id='cursor'
                    />
                    <Line ref="graph" id='data-chart' data={data} options={options}/>

                </div>
            </div>
        );
    }
}

export default App;
