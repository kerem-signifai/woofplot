import React, {Component} from 'react';
import {Button, Dropdown} from 'semantic-ui-react';
import {Line} from 'react-chartjs-2';

import 'chartjs-plugin-colorschemes';
import './App.css';

const api = 'https://c7ccd1b7.ngrok.io/api/';

class App extends Component {
    constructor(props) {
        super(props);
        this.chartRef = React.createRef();
        this.state = {
            yAxes: [],
            forceAxesUpdate: false,
            prePlotError: null,
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

    componentDidUpdate(prevProps) {
        const {yAxes, forceAxesUpdate} = this.state;
        const chart = this.chartRef.current.chartInstance;

        if (forceAxesUpdate && chart != null) {
            chart.options.scales.yAxes = yAxes;
            chart.update();
            this.setState({forceAxesUpdate: false});
        }
    }

    componentWillUnmount() {
        clearInterval(this.interval);
    }

    componentDidMount() {
        this.interval = setInterval(() => this.handleLoad(true), 60 * 1000);

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
                cursorCtx.moveTo(clientX - offsetLeft, this.state.datasets.length > 0 ? 32 : 10);
                cursorCtx.lineTo(clientX - offsetLeft, dataHeight - 30);
                cursorCtx.stroke();
            }
        };
        chartContainer.onmouseleave = () => {
            cursorCtx.clearRect(0, 0, cursorCanvas.width, cursorCanvas.height);
        };

        this.setState({isFetching: true});
        fetch(`${api}woofs`)
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
                            datatype: dataType,
                            value: id,
                            text: woof.name + ' [' + dataType + ']',
                            source: woof
                        }
                    }
                }));
                this.setState({availableWoofs: woofs, isFetching: false});
            });
    }

    buildYAxes = (datasets) => {
        const {scaleModifier} = this.state;

        let yAxes = [];
        let left = true;
        [...new Set(datasets.map(data => data.woof.datatype))].forEach(datatype => {
            yAxes.push({
                id: datatype,
                position: left ? 'left' : 'right',
                scaleLabel: {
                    display: true,
                    labelString: datatype,
                    fontSize: 20
                },
                afterDataLimits: (axis) => {
                    axis.max *= scaleModifier;
                    axis.min /= scaleModifier;
                }
            });
            left = false;
        });
        return yAxes;
    };

    handleLoad = (background) => {
        const {selectedWoofs, retentionMinutes, availableWoofs, prePlotError} = this.state;

        if (selectedWoofs.length > 0 && prePlotError == null) {
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
                        `${api}query?woofId=${woofId}&from=${Date.now() - retentionMinutes * 60 * 1000}&to=${Date.now()}&aggregation=average&interval=${interval}`
                    ).then(response => response.json())
                        .then(results => {
                            const woofRef = availableWoofs[woofId];
                            return {
                                label: woofRef.text,
                                yAxisID: woofRef.datatype,
                                woof: woofRef,
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
                    yAxes: this.buildYAxes(results),
                    forceAxesUpdate: true,
                    loadedRetentionMinutes: retentionMinutes,
                    scaleModifier: 1
                });
            });
        }
    };

    handleRangeSelect = (event, data) => {
        this.setState({retentionMinutes: data.value});
    };

    handleSourceSelect = (event, data) => {
        const {availableWoofs} = this.state;

        const woofs = data.value;
        this.setState({selectedWoofs: woofs});
        if ([...new Set(woofs.map(w => availableWoofs[w].datatype))].length > 2) {
            this.setState({
               prePlotError: "Too many data types selected!"
            });
        } else {
            this.setState({
                prePlotError: null
            })
        }
    };

    handleWheel = (e) => {
        this.setState({
            scaleModifier: Math.max(1, this.state.scaleModifier + e.deltaY / 5000.0)
        });
    };

    render() {
        const {isFetching, isLoading, availableWoofs, datasets, loadedRetentionMinutes, prePlotError, yAxes} = this.state;

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
                yAxes: yAxes,
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
                        disabled={isFetching || isLoading || prePlotError != null}
                        onClick={() => this.handleLoad(false)}
                    >
                        Plot
                    </Button>
                </span>
                <span className='info-text'>
                    {prePlotError}
                </span>
                <br style={{clear: 'both'}}/>
                <div className='chart-container' id='chart-wrapper' onWheel={(e) => this.handleWheel(e)}>

                    <canvas
                        id='cursor'
                    />
                    <Line ref={this.chartRef} id='data-chart' data={data} options={options} redraw={false}/>

                </div>
            </div>
        );
    }
}

export default App;
