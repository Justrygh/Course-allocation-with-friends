import collections
import datetime
import glob
import os
import re
import shutil
from pathlib import Path
from zipfile import ZipFile

import networkx
import numpy as np

import mallows_kendall as mk
import matplotlib.pyplot as plt
import pandas as pd
import seaborn as sns
import networkx as nx

from pyvis.network import Network
from distutils.dir_util import copy_tree

########################################
#        Create Experiment Data        #
########################################


students_index = {}


def create_friendship_csv(df: pd.DataFrame, weight: int) -> pd.DataFrame:
    index_students(df.get('Name'))
    friends_matrix = [[0 for _ in range(df.shape[0])] for _ in range(df.shape[0])]

    index = 0
    friendships = df[['F1', 'F2', 'F3']]
    for row in friendships.values:
        for i in range(len(row)):
            friends_matrix[index][int(students_index.get(row[i]))] = (len(row) - i) * weight
        index += 1
    return pd.DataFrame(friends_matrix, columns=[f'a{i+1}' for i in range(len(friends_matrix))])


def index_students(students: list):
    index = 0
    for stu in students:
        if stu not in students_index:
            students_index[stu] = index
            index += 1


def create_courses_csv(df: pd.DataFrame) -> pd.DataFrame:
    columns = {f'C{i+1}': f'c{i+1}' for i in range(9)}
    courses_dict_list = []

    courses = df[['CR1', 'CR2', 'CR3', 'CR4', 'CR5', 'CR6', 'CR7', 'CR8', 'CR9']]
    for row in courses.values:
        courses_dict_list.append(create_courses_row(columns=columns.keys(), courses=row))

    return pd.DataFrame(courses_dict_list).rename(columns=columns)


def create_courses_row(columns: list, courses: list) -> dict:
    courses_row = dict.fromkeys(columns)
    for i in range(len(courses)):
        courses_row[courses[i]] = (len(courses) - i)
    return courses_row


class Graph:

    def __init__(self):
        self.size = 0
        self.edges = []
        # directed graph
        self.graph = nx.DiGraph()
        # node adj
        self.nodes_adj = {}

    def upload_csv(self, csv_file: str):
        friends_matrix = pd.read_csv(csv_file)
        for from_v, row in friends_matrix.iterrows():
            for to_v, rate in row.items():
                self.edges.append((f'a{from_v+1}', to_v, rate)) if rate > 0 else None
        # update the friendship matrix size
        self.size = friends_matrix.shape[0]

    def create_nodes_adj(self):
        for adj in nx.generate_adjlist(self.graph, delimiter=","):
            for node in adj.split(","):
                self.nodes_adj[node] = self.nodes_adj.get(node, 0) + 1

    def build_graph(self, csv_file: str):
        self.upload_csv(csv_file)
        for edge in self.edges:
            self.graph.add_edge(edge[0], edge[1], weight=edge[2])

        self.create_nodes_adj()
        for i in range(self.size):
            self.graph.add_node(f'a{i + 1}', size=self.nodes_adj[f'a{i + 1}'])

    def plot_friendship_graph(self):
        net = Network("800px", "1200px", directed=True)
        net.from_nx(self.graph)

        net.save_graph(name='Results/friendships.html')

    def generate_statistics(self):
        degree_centrality = nx.in_degree_centrality(self.graph)

        index = 1
        reaching_centrality = {}
        for node in self.graph.nodes:
            reaching_centrality[f'a{index}'] = nx.local_reaching_centrality(self.graph, node)
            index += 1

        index = 0  
        degree_hist = {}
        for node in nx.degree_histogram(self.graph):
            if node != 0:
                degree_hist[f'Degree: {index-3}'] = node
            index += 1

        closeness_centrality = nx.closeness_centrality(self.graph)

        degree_vote_rank = nx.voterank(self.graph, self.size)

        print(f"===== In-Degree Centrality =====\n{degree_centrality}\n"
              f"\n===== Reaching Centrality =====\n{reaching_centrality}\n"
              f"\n===== Closeness Centrality =====\n{closeness_centrality}\n"
              f"\n===== In-Degree Histogram =====\n{degree_hist}\n"
              f"\n===== Degree Vote Rank =====\n{degree_vote_rank}\n")

########################################
#               Generic                #
########################################


weights = set()
experiment_index = set()

algorithms = ["DSA_RC", "DSA", "Greedy", "RSD", "HBS", "Random"]
groups = [["DSA_RC", "RSD", "HBS"], ["DSA", "Greedy", "Random"]]
fieldnames = ["utility", "courses", "gini", "friends", "first_agent", "mid_agent", "last_agent"]
markers = {"DSA_RC": 'D', "DSA": 'P', "Greedy": 'o', "RSD": 's', "HBS": 'X', "Random": "H"}
colors = {"DSA_RC": 'blue', "DSA": 'brown', "Greedy": 'green', "RSD": 'orange', "HBS": 'purple', "Random": 'red'}

descriptions = {
    "utility": "Total utility",
    "courses": "Number of illegal assignments",
    "gini": "Gini coefficient",
    "friends": "Number of friends shared among the courses",
    "first_agent": "First agent utility",
    "mid_agent": "Middle agent utility",
    "last_agent": "Last agent utility",
}


experiments_agents = None


def generate_data(students: int, courses: int, theta: float):
    for row in mk.sample(m=students, n=courses, theta=theta):
        print(",".join([str(i+1) for i in row]))


def create_dataframes(column: str = 'agents'):
    global experiments_agents
    experiments = {}
    for dir in glob.glob(f'Data/{column[0].upper() + column[1:]}/*_Agent'):
        experiment_name = os.path.basename(dir).replace("_Agent", "").replace('Iterative', 'RSD')
        exp_df = pd.DataFrame(columns=fieldnames)

        for file in os.listdir(dir):

            if os.path.isdir(os.path.join(dir, file)):
                continue

            filename = os.path.basename(file)
            agents = filename[:filename.find(column)]
            # add the agents to the experiment index
            experiment_index.add(int(agents))

            averages = dict.fromkeys(exp_df.columns)
            df = pd.read_csv(os.path.join(dir, file), names=exp_df.columns)
            for col in df.columns:
                col_list = df[col].tolist()
                averages[col] = (sum(col_list) / len(col_list))

            averages[column] = str(int(agents))
            exp_df = exp_df.append(averages, ignore_index=True)

        if experiments_agents is None:
            experiments_agents = sorted(exp_df.get(column))

        experiments[experiment_name] = Experiment(name=experiment_name, dataframe=exp_df.sort_values(column, ignore_index=True))
    return experiments


def plot_area(experiments: dict, column: str = 'agents'):
    areas = pd.DataFrame()
    for exp in experiments:
        for col in experiments.get(exp).get_dataframe():
            if col != column:
                experiments.get(exp).add_area(column=col, base=column)
        areas = areas.append(pd.DataFrame(experiments.get(exp).get_area(), index=[exp]))
    return "algorithms"+areas.to_csv()


def generate_graph(ax, path: str, column: str,  fieldname: str, ylabel: str = None):

    # If y axis label wasn't provided, use the default one
    if not ylabel:
        ylabel = descriptions[fieldname]

    # Shrink current axis's height by 20% on the bottom
    box = ax.get_position()
    ax.set_position([box.x0, box.y0 + box.height * 0.2,
                     box.width, box.height * 0.8])

    # Put a legend below current axis
    ax.legend(loc='upper center', bbox_to_anchor=(0.5, -0.15),
              fancybox=True, shadow=True, ncol=3)

    label = "Number of agents" if column == 'agents' else "Course Limit"
    ax.set(xlabel=label, ylabel=ylabel)
    plt.setp(ax.collections, sizes=[10])

    # plt.ylim([39, 79])
    plt.savefig(path)
    plt.clf()


def plot_graphs_binary(experiments: dict, column: str = 'agents'):
    for col in fieldnames:
        for exp in experiments:
            if not experiments.get(exp).get_unary():
                ax = sns.lineplot(data=experiments.get(exp).get_dataframe(), x=column, y=col, marker=markers.get(exp),
                                  markersize=8, label=exp, color=colors.get(exp))

        generate_graph(ax=ax, fieldname=col, column=column, path=f'Results/{column[0].upper() + column[1:]}/Friendship/Included/{col}_fig')


def plot_utility_graph(combined_experiments: dict, column: str = 'courseLimit'):
    for exp in combined_experiments:
        for weight in combined_experiments.get(exp):
            ax = sns.lineplot(data=combined_experiments.get(exp).get(weight).get_dataframe(), x=column, y='utility',
                              marker=markers.get(exp), markersize=8, label=f'{exp}{weight}')

        generate_graph(ax=ax, fieldname='utility', column=column, path=f'Results/{column[0].upper() + column[1:]}/Combined/Total/{exp}_fig')

    group_index = 1
    for group in groups:
        for exp in group:
            for weight in combined_experiments.get(exp):
                ax = sns.lineplot(data=combined_experiments.get(exp).get(weight).get_dataframe(), x=column, y='utility',
                                  marker=markers.get(exp), markersize=8, label=exp if weight == 'x0' else None, color=colors.get(exp))

        generate_graph(ax=ax, fieldname='utility', column=column, path=f'Results/{column[0].upper() + column[1:]}/Combined/Total/Group-{group_index}')
        group_index += 1


def plot_combined_graphs(combined_experiments: dict, column: str = 'courseLimit'):
    for index in experiment_index:
        for col in fieldnames:
            exp_df = {g: [] for g in algorithms}
            for exp in algorithms:
                for weight in combined_experiments.get(exp):
                    df = combined_experiments.get(exp).get(weight).get_dataframe()
                    # find the row that matches the query
                    row = df.loc[df[column] == f'{index}'][col].values[0]
                    exp_df[exp].append(row)

            tick = 1/(len(weights)+1)
            for weight in range(len(weights)):
                plt.bar(np.arange(len(algorithms)) + tick * weight, [value[weight] for value in exp_df.values()], tick)

            plt.xticks(np.arange(len(exp_df)), exp_df.keys())
            plt.legend([f'w={w}' for w in weights], loc='upper left')
            plt.ylabel(descriptions.get(col))

            plt.savefig(f'Results/{column[0].upper() + column[1:]}/Combined/{col[0].upper() + col.replace("_agent", "")[1:]}/{column}-{index}_fig.png')
            plt.clf()


def plot_reversed_graphs(combined_experiments: dict, column: str = 'courseLimit'):
    for index in experiment_index:
        for col in fieldnames:
            exp_df = {f'w={w}': [] for w in weights}
            for exp in algorithms:
                for weight in combined_experiments.get(exp):
                    df = combined_experiments.get(exp).get(weight).get_dataframe()
                    # find the row that matches the query
                    row = df.loc[df[column] == f'{index}'][col].values[0]
                    exp_df[f'w={weight[1]}'].append(row)

            tick = 1/(len(algorithms)+1)
            for algo in range(len(algorithms)):
                plt.bar(np.arange(len(weights)) + tick * algo, [value[algo] for value in exp_df.values()], tick)

            plt.xticks(np.arange(len(exp_df)), exp_df.keys())
            plt.legend(algorithms, loc='upper left')
            plt.ylabel(descriptions.get(col))

            plt.savefig(f'Results/{column[0].upper() + column[1:]}/Reversed/{col[0].upper() + col.replace("_agent", "")[1:]}/{column}-{index}_fig.png')
            plt.clf()


def plot_graphs_unary(experiments: dict, column: str = 'agents'):
    for col in fieldnames:
        for exp in experiments:
            if experiments.get(exp).get_unary():
                ax = sns.lineplot(data=experiments.get(exp).get_dataframe(), x=column, y=col, color=colors.get(exp[:exp.rfind("_Unary")]),
                                  marker=markers.get(exp[:exp.rfind("_Unary")]), markersize=8, label=exp[:exp.rfind("_")], linestyle="--")

        generate_graph(ax=ax, fieldname=col, column=column, path=f'Results/{column[0].upper() + column[1:]}/Friendship/Excluded/{col}_fig')


def plot_graphs_unary_vs_binary(experiments: dict, column: str = 'agents'):
    index = 0
    for g in groups:
        index += 1
        for col in fieldnames:
            for exp in g:
                ax = sns.lineplot(data=experiments.get(exp).get_dataframe(), x=column, y=col, marker=markers.get(exp),
                                  markersize=8, color=colors.get(exp), label=exp+" (w)")

            for exp in g:
                ax = sns.lineplot(data=experiments.get(exp+"_Unary").get_dataframe(), x=column, y=col, linestyle="--",
                                  marker=markers.get(exp), markersize=8, color=colors.get(exp), label=exp+" (w/o)")

            generate_graph(ax=ax, fieldname=col, column=column, path=f'Results/{column[0].upper() + column[1:]}/Groups/Group{index}/{col}_fig')


def plot_graphs_histogram(experiments: dict, column: str = 'agents'):
    global experiments_agents
    index = 0
    for g in groups:
        index += 1
        for metric in fieldnames:
            exp_df = {f'{i}': [] for i in experiments_agents}
            for exp in g:
                for i in range(len(experiments.get(exp).get_dataframe().get(metric))):
                    exp_df[str(int(experiments.get(exp).get_dataframe().get(column)[i]))].append(experiments.get(exp).get_dataframe().get(metric)[i])

            tick = 0.25
            for agent in range(len(g)):
                plt.bar(np.arange(len(exp_df)) + tick * agent, [i[agent] for i in exp_df.values()], tick)

            plt.xticks(np.arange(len(exp_df)), exp_df.keys())
            plt.xlabel("Number of agents" if column == 'agents' else "Course limit")
            plt.ylabel(rename_metric(metric[0].upper() + metric[1:]))

            plt.legend(g)
            plt.savefig(f'Results/{column[0].upper() + column[1:]}/Experiments/Group{index}/{metric}_fig')
            plt.clf()


def plot_graphs_bars(experiments: dict, column: str = 'agents'):
    global experiments_agents
    fields = [field for field in fieldnames if field.endswith('agent')]

    for index in experiments_agents:
        exp_df = {f'{g}': [] for g in algorithms}
        for g in algorithms:
            df = experiments.get(g).get_dataframe()
            # find the row that matches the query
            row = df.loc[df[column] == index]
            # insert the fields to the dict
            for i in fields:
                exp_df[g].append(float(row.get(i)))

        tick = 0.25
        for agent in range(len(fields)):
            plt.bar(np.arange(len(algorithms)) + tick * agent, [i[agent] for i in exp_df.values()], tick)

        plt.xticks(np.arange(len(exp_df)), exp_df.keys())
        plt.legend([field[0].upper() + field.replace("_agent", "")[1:] for field in fields])

        plt.savefig(f'Results/{column[0].upper() + column[1:]}/Utility/{column}-{index}_fig')
        plt.clf()


def rename_metric(name):
    return re.sub(f'_agent', ' Agent Utility', name)


class Experiment:

    name: str
    unary: bool
    area: dict
    dataFrame: pd.DataFrame

    def __init__(self, name: str, dataframe: pd.DataFrame):
        self.name = name
        self.area = dict()
        self.unary = 'Unary' in self.name
        self.dataFrame = dataframe

    def get_dataframe(self):
        return self.dataFrame

    def get_unary(self):
        return self.unary

    def add_area(self, column: str, base: str = 'agents'):
        self.area[column] = self.calculate_area(column=column, base=base)

    def calculate_area(self, column: str, base: str = 'agents'):
        area = 0
        for i in range(self.dataFrame.shape[0] - 1):
            x1, x2, y1, y2 = self.dataFrame.iloc[i].get(base), self.dataFrame.iloc[i + 1].get(base), \
                             self.dataFrame.iloc[i].get(column), self.dataFrame.iloc[i + 1].get(column)
            area += (float(x2) - float(x1)) * (float(y1) + float(y2)) / 2
        return round(area, 2)

    def calculate_variance(self, column):
        pass

    def get_area(self):
        return self.area


def create_dirs():
    dirs = ['Data/*', 'Results/*/Groups/Group1', 'Results/*/Groups/Group2', 'Results/*/Friendship/Excluded',
            'Results/*/Friendship/Included', 'Results/*/Experiments/Group1', 'Results/*/Experiments/Group2',
            'Results/*/Utility', 'Results/*/#/Utility', 'Results/*/#/Courses', 'Results/*/#/Gini', 'Results/*/#/Friends',
            'Results/*/#/First', 'Results/*/#/Mid', 'Results/*/#/Last', 'Results/*/Combined/Total']
    for directory in dirs:
        for exp in ['Agents', 'CourseLimit']:
            for graph in ['Combined', 'Reversed']:
                os.makedirs(name=directory.replace('*', exp).replace('#', graph), exist_ok=True)


def save_results(column: str, exps: dict, base_path: Path, date: str, exp_details: str):
    """ Calculate area beneath graphs"""
    with open(f'Results/{column[0].upper() + column[1:]}/Area.csv', 'w') as fp:
        fp.write(plot_area(experiments=exps, column=column))

    column = column[0].upper() + column[1:]

    result_base_path = base_path.joinpath('Results')
    result_files_dir = result_base_path.joinpath(date, column, exp_details)

    os.makedirs(name=result_files_dir, exist_ok=True)
    copy_tree(str(result_base_path.joinpath(column)), str(result_files_dir))

    # base_path.joinpath('Archives', file).rename(base_path.joinpath('Archives', 'old', file))


def main():
    global weights, experiment_index
    create_dirs()

    """ Generate Experiment Data """
    # df = pd.read_csv('./Courses_Friends.csv')
    # create_friendship_csv(df).to_csv('friends.csv', index=False, header=True)
    # create_courses_csv(df).to_csv('courses.csv', index=False, header=True)

    """ Generate Random Data """
    # generate_data(students=146, courses=9, theta=1.5)

    """ Working with zip files """
    base_column = None
    base_path = Path(__file__).parent
    files = os.listdir(base_path.joinpath('Archives'))
    for file in sorted(files):
        if file.startswith("courseAlgo") and file.endswith(".zip"):
            date, column, exp_details = re.match(f'^courseAlgo_([^_]+)_([^_]+)_([^_]+).zip', file).groups()
            ZipFile(base_path.joinpath('Archives', file)).extractall(path=base_path.joinpath('Data', column))

            weights.add(int(exp_details[1:]))
            base_column = column

            if exp_details == 'x0':
                exps = create_dataframes(column=column)
                plot_graphs_histogram(experiments=exps, column=column)
                plot_graphs_bars(experiments=exps, column=column)

                save_results(column=column, exps=exps, base_path=base_path, date=date, exp_details=exp_details)

                for dir in os.listdir(base_path.joinpath('Data', column)):
                    base_path.joinpath('Data', column, dir).replace(base_path.joinpath('Data', column, f'{dir.replace("Agent", "Unary")}_Agent'))
                continue

            exps = create_dataframes(column=column)
            plot_graphs_histogram(experiments=exps, column=column)
            plot_graphs_binary(experiments=exps, column=column)
            plot_graphs_unary(experiments=exps, column=column)
            plot_graphs_unary_vs_binary(experiments=exps, column=column)
            plot_graphs_bars(experiments=exps, column=column)

            save_results(column=column, exps=exps, base_path=base_path, date=date, exp_details=exp_details)

    """ Advanced graphs - combined """
    exps = combine_experiments()

    # convert the weights and the experiment_index to list and sort
    weights = list(sorted(weights))
    experiment_index = list(sorted(experiment_index))

    plot_utility_graph(exps, column=base_column)
    plot_combined_graphs(exps, column=base_column)
    plot_reversed_graphs(combined_experiments=exps, column=base_column)

    """ Create Friendship Matrix Graph """
    if base_column == 'courseLimit':
        g = Graph()
        g.build_graph('friendship.csv')
        g.plot_friendship_graph()
        g.generate_statistics()


def combine_experiments():
    base_path = Path(__file__).parent
    files = os.listdir(base_path.joinpath('Archives'))
    all_exps = {key: {inner_key: {} for inner_key in [f'x{weight}' for weight in weights]} for key in algorithms}

    for file in files:
        if file.startswith("courseAlgo") and file.endswith(".zip"):
            date, column, exp_details = re.match(f'^courseAlgo_([^_]+)_([^_]+)_([^_]+).zip', file).groups()
            ZipFile(base_path.joinpath('Archives', file)).extractall(path=base_path.joinpath('Data', column))

            exps = create_dataframes(column=column)
            for key in exps:
                if not key.endswith('Unary'):
                    all_exps[key].update({exp_details: exps[key]})
                else:
                    all_exps[key.replace("_Unary", "")].update({'x0': exps[key]})

    return all_exps


def self_extract():
    base_path = Path(__file__).parent
    for file in os.listdir(base_path.joinpath('Raw')):
        if file.startswith("courseAlgo") and file.endswith(".zip"):
            ZipFile(base_path.joinpath('Raw', file)).extractall(base_path.joinpath('Raw'))

    for file in os.listdir(base_path.joinpath('Raw')):
        if os.path.isdir(base_path.joinpath('Raw', file)):
            for exp in os.listdir(base_path.joinpath('Raw', file)):
                data, weight = re.match(f'^\d+(agents|courseLimit)_(\d+)weight.csv$', exp).groups()
                os.makedirs(base_path.joinpath('Exps', data, f'x{weight}', file), exist_ok=True)
                base_path.joinpath('Raw', file, exp).replace(base_path.joinpath('Exps', data, f'x{weight}', file, f'{exp[:exp.find("_")]}.csv'))

    for exp in os.listdir(base_path.joinpath('Exps')):
        for weight in os.listdir(base_path.joinpath('Exps', exp)):
            filename = f'courseAlgo_{datetime.datetime.now().date()}_{exp}_{weight}'
            shutil.make_archive(base_path.joinpath('Archives', filename), 'zip', base_path.joinpath('Exps', exp, weight))


main()
